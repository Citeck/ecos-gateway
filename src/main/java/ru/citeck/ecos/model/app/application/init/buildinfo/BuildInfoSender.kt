package ru.citeck.ecos.model.app.application.init.buildinfo

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.util.ResourceUtils
import ru.citeck.ecos.commands.CommandsService
import ru.citeck.ecos.commands.annotation.CommandType
import ru.citeck.ecos.commons.data.ObjectData
import ru.citeck.ecos.commons.json.Json.mapper
import ru.citeck.ecos.rabbitmq.RabbitMqConnProvider
import java.io.File
import java.io.FileNotFoundException
import java.lang.Exception
import java.time.Duration
import java.util.concurrent.Executor
import javax.annotation.PostConstruct

@Component
class BuildInfoSender(
    @Qualifier("taskExecutor")
    private val executor: Executor,
    private val rabbitMqConnProvider: RabbitMqConnProvider,
    private val commandsService: CommandsService
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @PostConstruct
    fun init() {

        val rabbitMqConn = rabbitMqConnProvider.getConnection()
        if (rabbitMqConn == null) {
            log.info { "RabbitMQ Connection is missing. Build Info wont be send" }
            return
        }
        val buildInfoFile: File? = try {
            ResourceUtils.getFile("classpath:build-info/full.json")
        } catch (e: FileNotFoundException) {
            // do nothing
            null
        } catch (e: Exception) {
            log.error(e) { "Build info file search error" }
            null
        }
        if (buildInfoFile == null || !buildInfoFile.exists()) {
            log.info { "Build info is not found" }
            return
        }
        val info = mapper.read(buildInfoFile, ObjectData::class.java) ?: return

        executor.execute {
            rabbitMqConn.waitUntilReady(Duration.ofHours(1).toMillis())
            sendBuildInfo(info)
        }
    }

    private fun sendBuildInfo(info: ObjectData) {

        commandsService.execute {
            targetApp = "uiserv"
            body = AddBuildInfoCommand(listOf(
                AppBuildInfo("emodel", "ECOS Model", "", info)
            ))
            ttl = Duration.ofHours(1)
        }
    }

    data class AppBuildInfo(
        val id: String,
        val label: String,
        val description: String,
        val info: ObjectData
    )

    @CommandType("uiserv.add-build-info")
    class AddBuildInfoCommand(
        val info: List<AppBuildInfo>
    )
}
