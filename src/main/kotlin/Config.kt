package org.example

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val port: Int = 80,
    val delay: Int = 10,
    val timeout: Int = 60,
    val messageTemplate: String = "This message sent at %s!"
)
