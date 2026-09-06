package dev.cfmobile.app.ui.d1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.dto.D1QueryResult
import dev.cfmobile.app.data.repository.D1Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class D1ConsoleUiState(
    val sql: String = "",
    val isRunning: Boolean = false,
    val results: List<D1QueryResult> = emptyList(),
    val ranSql: String? = null,
    val error: String? = null
)

/** SQL that changes data or schema, as opposed to a read. Used to warn before running one -
 *  there is no undo on a D1 database, and this is a phone. */
fun isMutatingSql(sql: String): Boolean {
    val firstWord = sql.trim()
        .removePrefix("(")
        .takeWhile { !it.isWhitespace() }
        .uppercase()
    return firstWord in MUTATING_STATEMENTS
}

private val MUTATING_STATEMENTS = setOf(
    "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER", "REPLACE", "TRUNCATE", "PRAGMA", "VACUUM"
)

/** Column order for a result set. Rows are JSON objects, so this takes the keys of the first
 *  row and keeps any extra keys later rows introduce - a sparse row must not silently drop a
 *  column from the table. */
fun columnsOf(result: D1QueryResult): List<String> {
    val rows = result.results.orEmpty()
    val columns = LinkedHashSet<String>()
    rows.forEach { columns.addAll(it.keys) }
    return columns.toList()
}

/** Renders a cell for display: null is shown as NULL rather than as an empty cell, so a null
 *  and an empty string don't look identical. */
fun formatCell(value: Any?): String = when (value) {
    null -> "NULL"
    is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    else -> value.toString()
}

/** "3 rows · 12ms" - built only from what Cloudflare returned. */
fun resultSummary(result: D1QueryResult): String {
    val rowCount = result.results?.size
    val parts = buildList {
        if (rowCount != null) add(if (rowCount == 1) "1 row" else "$rowCount rows")
        result.meta?.rowsWritten?.takeIf { it > 0 }?.let { add("$it written") }
        result.meta?.duration?.let { add("${"%.1f".format(it)}ms") }
    }
    return parts.joinToString(" · ").ifBlank { if (result.success) "OK" else "Failed" }
}

class D1ConsoleViewModel(
    private val accountId: String,
    private val databaseId: String,
    private val repository: D1Repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(D1ConsoleUiState())
    val uiState: StateFlow<D1ConsoleUiState> = _uiState.asStateFlow()

    fun updateSql(sql: String) = _uiState.update { it.copy(sql = sql, error = null) }

    fun run() {
        val sql = _uiState.value.sql.trim()
        if (sql.isBlank()) {
            _uiState.update { it.copy(error = "Enter a SQL statement to run") }
            return
        }
        _uiState.update { it.copy(isRunning = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.query(accountId, databaseId, sql)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(isRunning = false, results = result.data, ranSql = sql, error = null)
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isRunning = false, error = result.message)
                }
            }
        }
    }

    fun clear() = _uiState.update { it.copy(results = emptyList(), ranSql = null, error = null) }
}
