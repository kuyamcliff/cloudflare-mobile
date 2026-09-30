package dev.cfmobile.app.core.api

import com.google.common.truth.Truth.assertThat
import dev.cfmobile.app.ui.graphql.GraphQlValidator
import dev.cfmobile.app.ui.graphql.GraphQlViewModel
import dev.cfmobile.app.ui.search.SearchViewModel
import org.junit.Test

class MiscParsingTest {
    @Test fun `graphql result flattens the first array into a table`() {
        val body = """{"data":{"viewer":{"zones":[{"httpRequests1dGroups":[{"dimensions":{"date":"2026-09-28"},"sum":{"requests":1200.0}},{"dimensions":{"date":"2026-09-29"},"sum":{"requests":800.0}}]}]}},"errors":null}"""
        val (errors, table) = GraphQlViewModel.parse(body)
        assertThat(errors).isEmpty()
        assertThat(table!!.columns).containsExactly("dimensions.date", "sum.requests").inOrder()
        assertThat(table.rows).containsExactly(listOf("2026-09-28", "1200"), listOf("2026-09-29", "800")).inOrder()
    }

    @Test fun `graphql errors are surfaced`() {
        val (errors, _) = GraphQlViewModel.parse("""{"data":null,"errors":[{"message":"zone not authorized"}]}""")
        assertThat(errors).containsExactly("zone not authorized")
    }

    @Test fun `graphql validator catches structural problems before sending`() {
        assertThat(GraphQlValidator.validate("")).isNotNull()
        assertThat(GraphQlValidator.validate("{ viewer { zones { x } }")).isEqualTo("Unbalanced braces")
        assertThat(GraphQlValidator.validate("mutation { x }")).isNotNull()
        assertThat(GraphQlValidator.validate("query { viewer { a(filter: {b: \"}\"}) } }")).isNull()
    }

    @Test fun `search syntax narrows the source`() {
        assertThat(SearchViewModel.parse("zone:example.com")).isEqualTo("zone" to "example.com")
        assertThat(SearchViewModel.parse("API: r2")).isEqualTo("api" to "r2")
        assertThat(SearchViewModel.parse("https://x")).isEqualTo(null to "https://x")
    }
}
