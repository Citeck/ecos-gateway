package ru.citeck.ecos.gateway.security

import ru.citeck.ecos.commons.data.DataValue
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.records2.request.error.RecordsError
import ru.citeck.ecos.records2.request.result.RecordsResult
import ru.citeck.ecos.records3.record.request.msg.ReqMsg
import ru.citeck.ecos.records3.rest.v1.RequestResp
import java.util.regex.Pattern
import java.util.stream.Collectors

object RecordsSecurityUtils {

    private val CLASS_PATTERN: Pattern = Pattern.compile("([a-z0-9]+\\.)+[A-Z][a-zA-Z0-9]*")
    private val CLASS_LINE_PATTERN: Pattern = Pattern.compile("\\([a-zA-Z0-9]+\\.java:(\\d+)\\)")

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
        result.errors = result.errors
            .stream()
            .map { encodeError(it) }
            .collect(Collectors.toList())
        return result
    }

    fun encodeError(error: RecordsError?): RecordsError? {
        if (error == null) {
            return null
        }

        error.msg = encodeClasses(error.msg)

        val stackTrace = error.stackTrace
        if (stackTrace != null && stackTrace.isNotEmpty()) {
            error.stackTrace = stackTrace
                .stream()
                .map { obj: String? -> encodeClasses(obj) }
                .collect(Collectors.toList())
        }

        return error
    }

    private fun encodeClasses(str: String?): String? {
        if (str == null) {
            return null
        }

        var matcher = CLASS_PATTERN.matcher(str)
        var resultStr: String = str

        val builder = StringBuilder()

        while (matcher.find()) {
            val className = matcher.group(0)
            val packageAndClass = className.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (packageAndClass.size < 2) {
                continue
            }

            builder.setLength(0)
            for (i in 0..<packageAndClass.size - 1) {
                builder.append(packageAndClass[i][0])
            }

            val classShortName = packageAndClass[packageAndClass.size - 1]
            builder.append(classShortName.replace("[a-z]".toRegex(), ""))

            resultStr = resultStr.replace(className, builder.toString())
        }

        matcher = CLASS_LINE_PATTERN.matcher(resultStr)
        while (matcher.find()) {
            resultStr = resultStr.replace(matcher.group(0), matcher.group(1))
        }

        return resultStr
    }
}
