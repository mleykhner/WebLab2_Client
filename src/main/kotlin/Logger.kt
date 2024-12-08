package org.example

import java.io.FileOutputStream

class Logger(private val logStream: FileOutputStream) {

    fun log(message: String) {
        logStream.write((message + "\n").toByteArray())
        println(message)
    }

}