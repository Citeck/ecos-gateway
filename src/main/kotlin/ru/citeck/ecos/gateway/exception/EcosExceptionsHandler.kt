package ru.citeck.ecos.gateway.exception

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus

@ControllerAdvice
class EcosExceptionsHandler {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handle(exception: Exception, request: HttpServletRequest) {
        log.error(exception) { "HTTP 400 Bad Request" }
    }
}
