package org.example

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.*
import java.net.Socket
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun main(args: Array<String>) {
    val parser = ArgParser("example")
    val logPath by parser.option(ArgType.String, shortName = "l", description = "Path to log file").default("log.txt")
    val configPath by parser.option(ArgType.String, shortName = "c", description = "Path to config file")
        .default("config.json")
    parser.parse(args)

    val config = try {
        val configJson = File(configPath).readText()
        Json.decodeFromString<Config>(configJson)
    } catch (e: SerializationException) {
        println("Error: Incorrect JSON format in config file $configPath. ${e.message}")
        return
    } catch (e: IllegalArgumentException) {
        println("Error: Incorrect path to config file $configPath. ${e.message}")
        return
    }

    val logStream = try {
        File(logPath).outputStream()
    } catch (e: IOException) {
        println("Error: Unable to open log file $logPath. ${e.message}")
        return
    }

    val logger = Logger(logStream)
    val datePattern = DateTimeFormatter.ofPattern("HH:mm:ss")

    runBlocking {
        try {
            val socket = Socket(config.host, config.port)
            logger.log("Connected to ${config.host}:${config.port} at ${LocalTime.now().format(datePattern)}\n")

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = OutputStreamWriter(socket.getOutputStream())

            val sendJob = launch(Dispatchers.IO) {
                val startTime = LocalTime.now()
                while (isActive && startTime.plus(Duration.ofSeconds(config.timeout.toLong())) > LocalTime.now()) {
                    try {
                        val message = config.messageTemplate.format(LocalTime.now().format(datePattern))
                        writer.write(message)
                        writer.flush()
                        logger.log("[${LocalTime.now().format(datePattern)}] Sent: $message")
                        delay(config.delay.toLong() * 1000)
                    } catch (e: Exception) {
                        logger.log("Error sending message: ${e.message}")
                        break
                    }
                }
                socket.shutdownOutput()
                logger.log("Asked server to close (FIN sent) at ${LocalTime.now().format(datePattern)}")
            }

            val receiveJob = launch(Dispatchers.IO) {
                val buffer = CharArray(1024)
                try {
                    while (isActive) {
                        val bytesRead = reader.read(buffer)
                        val receivedMessage = try {
                            String(buffer, 0, bytesRead)
                        } catch (e: Exception) {
                            break
                        }

                        if (receivedMessage.isEmpty()) break
                        logger.log("[${LocalTime.now().format(datePattern)}] Received: $receivedMessage")
                    }
                    if (socket.isOutputShutdown) logger.log("No more data from server (server sent FIN) at ${LocalTime.now().format(datePattern)}")
                    else logger.log("Connection closed unexpectedly at ${LocalTime.now().format(datePattern)}")
                } catch (e: Exception) {
                    logger.log("Error receiving message: ${e.message}")
                }
            }

            joinAll(sendJob, receiveJob)
            socket.close()
        } catch (e: Exception) {
            logger.log("Connection error: ${e.message}")
        }
    }

}