package dev.cfmobile.app.ui.transformrules

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransformRuleWriteTest {

    @Test
    fun `every kind requires an expression`() {
        val form = TransformRuleForm(kind = TransformRuleKind.URL_REWRITE, expression = "", pathValue = "/new")
        assertThat(validateTransformForm(form)).isEqualTo("Expression is required")
    }

    @Test
    fun `URL rewrite requires a path or query value`() {
        val blank = TransformRuleForm(kind = TransformRuleKind.URL_REWRITE, pathValue = "", queryValue = "")
        assertThat(validateTransformForm(blank)).isEqualTo("Set a path and/or query rewrite")

        val withPath = blank.copy(pathValue = "/new")
        assertThat(validateTransformForm(withPath)).isNull()
    }

    @Test
    fun `header rules require a header name, and a value when setting`() {
        val noName = TransformRuleForm(kind = TransformRuleKind.REQUEST_HEADERS, headerName = "")
        assertThat(validateTransformForm(noName)).isEqualTo("Header name is required")

        val setNoValue = TransformRuleForm(kind = TransformRuleKind.REQUEST_HEADERS, headerName = "X-Foo", headerOperation = "set", headerValue = "")
        assertThat(validateTransformForm(setNoValue)).isEqualTo("A value or expression is required to set a header")

        val removeNoValue = TransformRuleForm(kind = TransformRuleKind.REQUEST_HEADERS, headerName = "X-Foo", headerOperation = "remove", headerValue = "")
        assertThat(validateTransformForm(removeNoValue)).isNull()
    }

    @Test
    fun `URL rewrite write always uses the rewrite action and populates uri`() {
        val form = TransformRuleForm(
            kind = TransformRuleKind.URL_REWRITE, expression = "true",
            pathValue = "/new-path", pathIsExpression = false,
            queryValue = "concat(\"a=\", http.request.uri.query)", queryIsExpression = true
        )

        val write = buildTransformRuleWrite(form)

        assertThat(write.action).isEqualTo("rewrite")
        assertThat(write.actionParameters?.uri?.path?.value).isEqualTo("/new-path")
        assertThat(write.actionParameters?.uri?.path?.expression).isNull()
        assertThat(write.actionParameters?.uri?.query?.expression).isEqualTo("concat(\"a=\", http.request.uri.query)")
        assertThat(write.actionParameters?.uri?.query?.value).isNull()
        assertThat(write.actionParameters?.headers).isNull()
    }

    @Test
    fun `an empty path or query is omitted rather than sent as a blank rewrite`() {
        val form = TransformRuleForm(kind = TransformRuleKind.URL_REWRITE, expression = "true", pathValue = "/new", queryValue = "")

        val write = buildTransformRuleWrite(form)

        assertThat(write.actionParameters?.uri?.path).isNotNull()
        assertThat(write.actionParameters?.uri?.query).isNull()
    }

    @Test
    fun `setting a header write carries a static value`() {
        val form = TransformRuleForm(
            kind = TransformRuleKind.REQUEST_HEADERS, expression = "true",
            headerName = "X-Custom", headerOperation = "set", headerValue = "hello", headerIsExpression = false
        )

        val write = buildTransformRuleWrite(form)

        assertThat(write.actionParameters?.headers?.get("X-Custom")?.operation).isEqualTo("set")
        assertThat(write.actionParameters?.headers?.get("X-Custom")?.value).isEqualTo("hello")
        assertThat(write.actionParameters?.headers?.get("X-Custom")?.expression).isNull()
    }

    @Test
    fun `setting a header write carries a dynamic expression instead of a value`() {
        val form = TransformRuleForm(
            kind = TransformRuleKind.RESPONSE_HEADERS, expression = "true",
            headerName = "X-Country", headerOperation = "set", headerValue = "ip.geoip.country", headerIsExpression = true
        )

        val write = buildTransformRuleWrite(form)

        assertThat(write.actionParameters?.headers?.get("X-Country")?.expression).isEqualTo("ip.geoip.country")
        assertThat(write.actionParameters?.headers?.get("X-Country")?.value).isNull()
    }

    @Test
    fun `removing a header write carries neither a value nor an expression`() {
        val form = TransformRuleForm(kind = TransformRuleKind.REQUEST_HEADERS, expression = "true", headerName = "X-Drop", headerOperation = "remove")

        val write = buildTransformRuleWrite(form)

        val header = write.actionParameters?.headers?.get("X-Drop")!!
        assertThat(header.operation).isEqualTo("remove")
        assertThat(header.value).isNull()
        assertThat(header.expression).isNull()
    }
}
