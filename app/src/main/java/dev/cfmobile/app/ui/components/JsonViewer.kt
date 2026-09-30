package dev.cfmobile.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.squareup.moshi.JsonReader
import dev.cfmobile.app.core.api.EndpointRegistry
import okio.Buffer

/** Pretty-prints JSON for display. Falls back to the raw text for anything that is not JSON.
 *  Callers run this off the main thread for large bodies (spec 240). */
object JsonFormat {
    fun pretty(raw: String): String {
        val trimmed = raw.trim()
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return raw
        return try {
            val value = JsonReader.of(Buffer().writeUtf8(trimmed)).readJsonValue()
            EndpointRegistry.prettyJson(value)
        } catch (e: com.squareup.moshi.JsonEncodingException) {
            raw
        } catch (e: com.squareup.moshi.JsonDataException) {
            raw
        } catch (e: java.io.EOFException) {
            raw
        }
    }

    fun isValidJson(raw: String): Boolean = try {
        JsonReader.of(Buffer().writeUtf8(raw.trim())).readJsonValue()
        true
    } catch (e: java.io.IOException) {
        false
    } catch (e: com.squareup.moshi.JsonDataException) {
        false
    }
}

/** Colors for syntax highlighting, chosen from the theme so both light and dark read well. */
data class JsonColors(val key: Color, val string: Color, val number: Color, val keyword: Color, val plain: Color)

@Composable
fun jsonColors() = JsonColors(
    key = MaterialTheme.colorScheme.primary,
    string = MaterialTheme.colorScheme.tertiary,
    number = MaterialTheme.colorScheme.secondary,
    keyword = MaterialTheme.colorScheme.error,
    plain = MaterialTheme.colorScheme.onSurface
)

private val TOKEN = Regex("(\"(?:\\\\.|[^\"\\\\])*\")(\\s*:)?|(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)|(true|false|null)")

fun highlightJsonLine(line: String, colors: JsonColors, query: String = ""): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in TOKEN.findAll(line)) {
        append(line.substring(last, m.range.first))
        val color = when {
            m.groups[1] != null && m.groups[2] != null -> colors.key
            m.groups[1] != null -> colors.string
            m.groups[3] != null -> colors.number
            else -> colors.keyword
        }
        withStyle(SpanStyle(color = color)) { append(m.value) }
        last = m.range.last + 1
    }
    append(line.substring(last))
    if (query.isNotBlank()) {
        var idx = line.indexOf(query, ignoreCase = true)
        while (idx >= 0) {
            addStyle(SpanStyle(background = colors.key.copy(alpha = 0.25f)), idx, idx + query.length)
            idx = line.indexOf(query, idx + query.length, ignoreCase = true)
        }
    }
}

/**
 * Adds a line-numbered, highlighted, horizontally scrollable JSON view to a LazyColumn. Lines
 * are virtualized, so a large response never lays out more than what is on screen (spec 111).
 */
fun LazyListScope.jsonLines(lines: List<String>, colors: JsonColors, query: String) {
    itemsIndexed(lines, key = { i, _ -> "json-$i" }) { i, line ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(
                "${i + 1}",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.width(40.dp)
            )
            Text(
                highlightJsonLine(line, colors, query),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}
