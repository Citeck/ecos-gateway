package ru.citeck.ecos.gateway.security

import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.records2.request.error.RecordsError
import ru.citeck.ecos.records2.request.result.RecordsResult
import ru.citeck.ecos.records3.record.request.msg.ReqMsg
import ru.citeck.ecos.records3.rest.v1.RequestResp

object RecordsSecurityUtils {

    private val CLASS_PATTERN = "([a-z0-9]+\\.)+[A-Z][a-zA-Z0-9]*".toRegex()
    private val CLASS_LINE_PATTERN = "\\([a-zA-Z0-9]+\\.java:(\\d+)\\)".toRegex()
    private val LOWERCASE_PATTERN = "[a-z]".toRegex()

    fun encodeResult(result: RequestResp) {
        val messages: List<ReqMsg> = result.messages
        if (messages.isEmpty()) {
            return
        }

        result.setMessages(messages.map { m: ReqMsg ->
            if (RecordsError.MSG_TYPE != m.type) {
                return@map m
            }
            val error = mapper.convert(m.msg, RecordsError::class.java)!!
            m.copy().withMsg(DataValue.create(encodeError(error))).build()
        })
    }

    fun <T> encodeResult(result: RecordsResult<T>): RecordsResult<T> {
        result.errors = result.errors.map { encodeError(it) }
        return result
    }

    fun encodeError(error: RecordsError?): RecordsError? {
        if (error == null) {
            return null
        }

        error.msg = encodeClasses(error.msg)

        val stackTrace = error.stackTrace
        if (stackTrace != null && stackTrace.isNotEmpty()) {
            error.stackTrace = stackTrace.map { encodeClasses(it) }
        }

        return error
    }

    private fun encodeClasses(str: String?): String? {
        if (str == null) {
            return null
        }

        val builder = StringBuilder()

        var resultStr = CLASS_PATTERN.replace(str) { matchResult ->
            val className = matchResult.value
            val packageAndClass = className.split(".")
            if (packageAndClass.size < 2) {
                className
            } else {
                builder.setLength(0)
                for (i in 0..<packageAndClass.size - 1) {
                    builder.append(packageAndClass[i][0])
                }
                val classShortName = packageAndClass[packageAndClass.size - 1]
                builder.append(classShortName.replace(LOWERCASE_PATTERN, ""))
                builder.toString()
            }
        }

        resultStr = CLASS_LINE_PATTERN.replace(resultStr) { matchResult ->
            matchResult.groupValues[1]
        }

        return resultStr
    }
}
