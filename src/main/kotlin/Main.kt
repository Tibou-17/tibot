package org.example

import club.minnced.discord.jdave.interop.JDaveSessionFactory
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.audio.AudioModuleConfig
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.RateLimitedException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.cache.CacheFlag
import javax.security.auth.login.LoginException

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

        get_conf().server_custom_conf.find {
            it.server_id == event.guild.id && it.delete_query_message
        }?.let { event.message.delete().queue() }

        println("QUERY : " + msg_content)

        if (ses == null && (!msg_content.startsWith("_play") && !msg_content.startsWith("_help"))){
            event.channel.sendMessage("Aucune session de musique n'est en cours.\n**Utilisez _help pour obtenir de l'aide.**").queue()
            return
        }

        when(msg_content.split(" ")[0]) {
            "_play" -> {
                val user_audio_channel: AudioChannel? = event.member?.voiceState?.channel as AudioChannel?

                if (user_audio_channel == null) {
                    println("L'utilisateur n'est pas connecté à un salon vocal.")
                    event.channel.sendMessage("Vous devez être connecté à un salon vocal pour utiliser cette commande.").queue()
                    return
                }

                if (ses != null) {
                    if (!ses.audioManager.isConnected)
                        ses.connect(user_audio_channel, event.channel)

                    ses.play(event)
                } else {
                    AudioSession(event).run {
                        sessions.add(this)
                        connect(user_audio_channel, event.channel)
                        play(event)
                    }
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
                event.channel.sendMessage("La file d'attente a été vidée${if (msg_content.contains("--all")) " et la bande son actuelle supprimée." else "."}").queue()
            }
            "_list" -> {
                if (ses == null || (ses.trackManager.queue.isEmpty() && ses.audioPlayer?.playingTrack == null)) {
                    event.channel.sendMessage("File d'attente vide.").queue()
                    return
                }

                if (msg_content.contains("--delete|-d".toRegex())) {
                    val args = msg_content.split("--delete|-d".toRegex())
                    val numberToDelete = if (args.size > 1) args[1].trim().toIntOrNull() else null
                    println("delete $numberToDelete with queue size = ${ses.trackManager.queue.size}")
                    if (numberToDelete != null) {
                        if (numberToDelete > 0 && numberToDelete <= ses.trackManager.queue.size) {
                            val trackToDelete = ses.trackManager.queue.elementAt(numberToDelete -1)
                            event.channel.sendMessage(
                                "La bande son ${format_track_title(trackToDelete)} a été retirée de la file d'attente.")
                                .queue()
                            ses.trackManager.queue.remove(trackToDelete)
                        }
                    }
                    return
                }

                val prefix = ses.audioPlayer.playingTrack.let {
                    "**" + format_track_title(it) + " [" + human_readable_duration(it.position) + "/" +
                    human_readable_duration(it.duration) + "]**\n"
                }
                val totalDuration = ses.trackManager.queue.sumOf { it.duration }

                event.channel.sendMessage(
                    ses.trackManager.queue.withIndex().joinToString(separator = "", prefix = prefix)
                    { (index, track) -> "$index. " + format_track_title(track) + " **[" + human_readable_duration(track.duration) + "]**\n" }
                    .take(1930).let { it.substring(0, it.lastIndexOf('\n')) }
                    + "\n. . .\n**__${ses.trackManager.queue.size} bandes son au total dans la file d'attente__ [${human_readable_duration(totalDuration)}]**")
                    .queue()
            }
            "_chut" -> {
                if (ses?.audioPlayer?.isPaused == true)
                    event.channel.sendMessage("Reprise de la lecture de la bande son actuelle.").queue()
                else
                    event.channel.sendMessage("Arrêt de la lecture de la bande son actuelle.").queue()
                ses?.audioPlayer?.isPaused = !ses.audioPlayer.isPaused
            }
            "_leave" -> {
                getSession(event.guild.id)?.let {
                    it.audioManager.closeAudioConnection()
                    it.trackManager.clearQueue(it.audioPlayer, true)
                    sessions.remove(it)
                    println("Fermeture de la session ${event.guild.id} avec la commande _leave")
                    event.channel.sendMessage("Fermeture de la session audio.").queue()
                }
            }
            "_help" -> {
                event.channel.sendMessage("""
                    Utilisation :
                    **_play** *<url | texte à chercher>* [--first] [--random] [--all]
                    -# Effectue une recherche de l'URL ou du texte, puis ajoute le résultat à la file d'attente
                    -# Si l'option --first est spécifier la ou les bandes son serons ajouter juste après la bande son actuels
                    -# Si l'option --random est spécifier mélange aléatoirement la playlist avant de l'ajouter dans la file d'attente
                    -# Si l'options --all est spécifier cela ajoutera à la file d'attente toutes les bandes son du résultat de la recherche (par défaut seul le meilleur résultat est ajouter).
                    **_skip** [n]
                    -# Passer à la n prochaine bandes son dans la file d'attente. (n max = 100).
                    -# Si l'options --all est spécifier toutes les bandes son seront passées (équivalent à clear --all).
                    **_clear**
                    -# Vide la file d'attente
                    -# Si l'options --all est spécifier cela enlevera la bande son actuel également.
                    **_list** [-d | --delete *<index>*]
                    -# Affiche la liste des bandes son dans la file d'attente ainsi que la bande son courante
                    -# Si l'option -d ou --delete est spécifiée, supprimez la bande son à l'index indiqué
                    **_chut**
                    -# Arrête ou fais reprendre la lecture de la bande-son actuelle
                    **_leave**
                    -# Ferme la session audio

                    Legende :
                        **texte gras** = à taper exactement comme indiqué
                        *<texte italique>* = à remplacer par l'argument approprié
                        [-abc] = tous les arguments entre [ ] sont facultatifs
                        -a|-b = les options séparées par | ne peuvent pas être utilisées simultanément
                """.trimIndent()).queue()
            }
        }
    }

    fun getSession(guildId: String): AudioSession? {
        return sessions.find { it.guild.id == guildId }
    }

    companion object {
        @Throws(IllegalArgumentException::class, LoginException::class, RateLimitedException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                JDABuilder.createLight(get_conf().discord_bot_api_token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_VOICE_STATES) // Use token provided as JVM argument
                    .enableCache(CacheFlag.VOICE_STATE)
                    .addEventListeners(MusicBot()) // Register new MusicBot instance as EventListener
                    .setActivity(Activity.customStatus("Écrivez _help ou _play"))
                    .setAudioModuleConfig(AudioModuleConfig().withDaveSessionFactory(JDaveSessionFactory()))
                    .build() // Build JDA - connect to discord
            } catch (e: Exception) {
                println(e.message)
            }
        }
    }
}

fun format_track_title(track: AudioTrack?): String {
    return "[${track?.info?.title?.replace("_", "")?.replace("*", "")}](<${track?.info?.uri}>)"
}

fun human_readable_duration(duration: Long): String {
    val hours = duration / 1000 / 60 / 60
    val minutes = (duration / 1000 / 60) % 60
    val seconds = (duration / 1000) % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}