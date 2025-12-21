package org.example

import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.RateLimitedException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import java.nio.file.Files
import javax.security.auth.login.LoginException
import kotlin.io.path.Path

class MusicBot : ListenerAdapter() {

    val sessions = ArrayList<AudioSession>()

    override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
        if (event.channelLeft != null) {
            if (event.channelLeft?.members?.size == 1) {
                if (event.channelLeft?.members?.first()?.id == event.jda.selfUser.id) {
                    getSession(event.guild.id)?.let {
                        it.audioManager.closeAudioConnection()
                        it.trackManager.clearQueue(it.audioPlayer, true)
                        sessions.remove(it)
                        println("Fermeture de la session ${event.guild.id}")
                    }
                }
            }
            if (event.entity.id == event.jda.selfUser.id && event.newValue == null) {
                getSession(event.guild.id)?.let {
                    it.audioManager.closeAudioConnection()
                    it.trackManager.clearQueue(it.audioPlayer, true)
                    sessions.remove(it)
                    println("Fermeture de la session ${event.guild.id} due à une déconnexion")
                }
            }
        }
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        // Make sure we only respond to events that occur in a guild
        if (!event.isFromGuild()) return
        if (event.getAuthor().isBot()) return
        val msg_content = event.message.contentRaw

        if (!msg_content.startsWith("_"))
            return

        val ses = getSession(event.guild.id)

        println(msg_content)

        if (ses == null && (!msg_content.startsWith("_play") && !msg_content.startsWith("_help"))){
            event.channel.sendMessage("Aucune session de musique n'est en cours.\n**Utilisez _help pour obtenir de l'aide.**").queue()
            return
        }

        when(msg_content.split(" ")[0]) {
            "_play" -> {
                if (ses != null) ses.play(event) else sessions.add(AudioSession(event))
            }
            "_quit" -> {
                getSession(event.guild.id)?.let {
                    it.audioManager.closeAudioConnection()
                    it.trackManager.clearQueue(it.audioPlayer, true)
                    sessions.remove(it)
                    println("Fermeture de la session ${event.guild.id}")
                    event.channel.sendMessage("Fermeture de la session de musique :white_check_mark:.").queue()
                }
            }
            "_skip" -> {
                if (ses?.audioPlayer?.playingTrack == null && ses?.trackManager?.queue?.isEmpty() == true) {
                    event.channel.sendMessage("Aucune bande son à passer.").queue()
                    return
                }
                if (msg_content.contains("--all")) {
                    ses?.trackManager?.clearQueue(ses.audioPlayer, true)
                    event.channel.sendMessage("Toutes les bandes son ont été passées.").queue()
                    return
                }
                val args = msg_content.split(" ")

                val numberToSkip = if (args.size > 1) args[1].toIntOrNull() else null

                if (numberToSkip == null || numberToSkip < 1)
                    event.channel.sendMessage("Bande son ${ses?.trackManager?.skip(ses.audioPlayer)} passer.").queue()
                else
                    event.channel.sendMessage("${ses?.trackManager?.skip(ses.audioPlayer, numberToSkip)} bandes son passer.").queue()
            }
            "_clear" -> {
                ses?.trackManager?.clearQueue(ses.audioPlayer, msg_content.contains("--all"))
            }
            "_list" -> {
                if (ses?.trackManager?.queue?.isEmpty() == true && ses.audioPlayer?.playingTrack == null)
                    event.channel.sendMessage("File d'attente vide.").queue()
                else {
                    val prefix = ses?.audioPlayer?.playingTrack?.let {
                        "**" + it.info.title + " [" + human_readable_duration(it.position) + "/" +
                        human_readable_duration(it.duration) + "]**\n"
                    }.orEmpty()

                    event.channel.sendMessage(
                        ses?.trackManager?.queue?.joinToString(separator = "", prefix = prefix, postfix = "# ...")
                        { "- " + it.info.title + " **[" + human_readable_duration(it.duration) + "]**\n" }.toString().take(1990)).queue()
                }
            }
            "_chut" -> {
                ses?.audioPlayer?.isPaused = !ses.audioPlayer.isPaused
            }
            "_help" -> {
                event.channel.sendMessage("""
                    Utilisation :
                    **_play** *<url | texte à chercher>* [--first] [--random] [--all]
                    -# Effectu une recherche de l'url ou du texte et l'ajoute à la file d'attente
                    -# Si l'option --first est spécifier la ou les bandes son serons ajouter juste après la bande son actuels
                    -# Si l'option --random est spécifier mélange aléatoirement la playlist avant de l'ajouter dans la file d'attente
                    -# Si l'options --all est spécifier cela ajoutera à la file d'attente toutes les bandes son du résultat de la recherche (par défaut seul le meilleur résultat est ajouter).
                    **_skip** [n]
                    -# Passer à la n prochaine bandes son dans la file d'attente. (n max = 100).
                    -# Si l'options --all est spécifier toutes les bandes son seront passées (équivalent à clear --all).
                    **_clear**
                    -# Vide la file d'attente
                    -# Si l'options --all est spécifier cela enlevera la bande son actuel également.
                    **_list**
                    -# Affiche la liste des bande son dans la file d'attente ainsi que la bande son courante
                    **_chut**
                    -# Faire taire le bot.
                    **_quit**
                    -# Ferme l'audiosession et déconnecte le bot"

                    Legende :
                        **texte gras** = à taper exactement comme indiqué
                        *<texte italique>* = à remplacer par l'argument approprié
                        [-abc] = tous les arguments entre [ ] sont facultatifs
                        -a|-b = les options séparées par | ne peuvent pas être utilisées simultanément
                """.trimIndent()).queue()
            }
        }
    }

    fun human_readable_duration(duration: Long): String {
        val minutes = duration / 1000 / 60
        val seconds = duration / 1000 % 60

        return "$minutes:${String.format("%02d", seconds)}"
    }

    fun getSession(guildId: String): AudioSession? {
        return sessions.find { it.guild.id == guildId }
    }

    companion object {
        @Throws(IllegalArgumentException::class, LoginException::class, RateLimitedException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                val token = Files.readString(Path(".token")).trim()
                JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_VOICE_STATES) // Use token provided as JVM argument
                    .enableCache(CacheFlag.VOICE_STATE)
                    .addEventListeners(MusicBot()) // Register new MusicBot instance as EventListener
                    .build() // Build JDA - connect to discord
            } catch (e: Exception) {
                println(e.message)
            }
        }
    }
}