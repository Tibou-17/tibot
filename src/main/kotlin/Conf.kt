package org.example

import dev.eav.tomlkt.Toml
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.nio.file.Files
import java.nio.file.Paths

@Serializable
data class Conf(
    val discord_bot_api_token: String,
    val server_custom_conf: List<ServerCustomConf>
)

@Serializable
data class ServerCustomConf(
    val server_id: String,
    val delete_query_message: Boolean
)

fun get_conf(config_file_path: String = "bot_conf.toml") : Conf {
    return Toml.decodeFromString(Files.readString(Paths.get(config_file_path)))
}

/* For tests
fun main() {
    val test_conf = """
        discord_bot_api_token = "test_token"

        [[server_custom_conf]]
        server_id = "1"
        delete_query_message = false

        [[server_custom_conf]]
        server_id = "2"
        delete_query_message = true
    """.trimIndent()
    val conf = Toml.decodeFromString<Conf>(test_conf)

    println(conf.discord_bot_api_token)
    println(conf.server_custom_conf.first().delete_query_message)
}*/