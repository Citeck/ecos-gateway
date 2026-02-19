package ru.citeck.ecos.gateway.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.records2.request.error.RecordsError
import ru.citeck.ecos.records2.request.result.RecordsResult
import ru.citeck.ecos.records3.record.request.msg.MsgLevel
import ru.citeck.ecos.records3.record.request.msg.ReqMsg
import ru.citeck.ecos.records3.rest.v1.query.QueryResp

class RecordsSecurityUtilsTest {

    // --- encodeError ---

    @Test
    fun `encodeError returns null for null input`() {
        assertThat(RecordsSecurityUtils.encodeError(null)).isNull()
    }

    @Test
    fun `encodeError encodes FQCN in message`() {
        val error = RecordsError("org.springframework.boot.Application failed")
        RecordsSecurityUtils.encodeError(error)
        assertThat(error.msg).isEqualTo("osbA failed")
    }

    @Test
    fun `encodeError encodes multiple FQCNs in message`() {
        val error = RecordsError("org.example.FooBar caused by com.test.pkg.MyError")
        RecordsSecurityUtils.encodeError(error)
        assertThat(error.msg).isEqualTo("oeFB caused by ctpME")
    }

    @Test
    fun `encodeError encodes line references`() {
        val error = RecordsError("at org.example.Foo(Foo.java:42)")
        RecordsSecurityUtils.encodeError(error)
        assertThat(error.msg).isEqualTo("at oeF42")
    }

    @Test
    fun `encodeError preserves message without class names`() {
        val error = RecordsError("simple error message")
        RecordsSecurityUtils.encodeError(error)
        assertThat(error.msg).isEqualTo("simple error message")
    }

    @Test
    fun `encodeError encodes stackTrace entries`() {
        val error = RecordsError()
        error.msg = "error"
        error.stackTrace = listOf(
            "at org.example.Service.run(Service.java:10)",
            "at com.test.App.main(App.java:5)"
        )
        RecordsSecurityUtils.encodeError(error)
        assertThat(error.stackTrace).containsExactly(
            "at oeS.run10",
            "at ctA.main5"
        )
    }

    // --- encodeResult(RequestResp) ---

    @Test
    fun `encodeResult RequestResp skips non-records-error messages`() {
        val resp = QueryResp()
        val msg = ReqMsg.create {
            type = "info"
            msg = DataValue.createStr("org.example.Foo happened")
            level = MsgLevel.ERROR
        }
        resp.setMessages(listOf(msg))
        RecordsSecurityUtils.encodeResult(resp)
        assertThat(resp.messages[0].msg.asText()).isEqualTo("org.example.Foo happened")
    }

    @Test
    fun `encodeResult RequestResp encodes records-error messages`() {
        val resp = QueryResp()
        val error = RecordsError("org.example.Service failed")
        val msg = ReqMsg.create {
            type = RecordsError.MSG_TYPE
            msg = DataValue.create(error)
            level = MsgLevel.ERROR
        }
        resp.setMessages(listOf(msg))
        RecordsSecurityUtils.encodeResult(resp)
        assertThat(resp.messages[0].msg.get("msg").asText()).isEqualTo("oeS failed")
    }

    // --- encodeResult(RecordsResult) ---

    @Test
    fun `encodeResult RecordsResult encodes all errors`() {
        val result = RecordsResult<Any>()
        result.addError(RecordsError("org.example.Foo"))
        result.addError(RecordsError("com.test.Bar"))
        RecordsSecurityUtils.encodeResult(result)
        assertThat(result.errors.map { it.msg }).containsExactly("oeF", "ctB")
    }
}
