package org.example

import club.minnced.discord.jdave.interop.JDaveSessionFactory
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.audio.AudioModuleConfig
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.exceptions.RateLimitedException
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import javax.security.auth.login.LoginException

@Throws(IllegalArgumentException::class, LoginException::class, RateLimitedException::class)
fun main(args: Array<String>) {
    try {
        JDABuilder.createLight(get_conf().discord_bot_api_token,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.MESSAGE_CONTENT,
            GatewayIntent.GUILD_MEMBERS,
            GatewayIntent.GUILD_VOICE_STATES)
            .enableCache(CacheFlag.VOICE_STATE)
            .addEventListeners(EventHandler())
            .setActivity(Activity.customStatus("Écrivez _help ou _play"))
            .setAudioModuleConfig(AudioModuleConfig().withDaveSessionFactory(JDaveSessionFactory()))
            .build()
    } catch (e: Exception) {
        println("FATAL ERROR: Fail to start | ${e.cause} ${e.message}")
    }
}