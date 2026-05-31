package org.example

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.components.buttons.Button
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder
import java.util.concurrent.LinkedBlockingDeque

class EventHandler : ListenerAdapter() {

    val sessions = ArrayList<AudioSession>()

    fun getSession(guildId: String): AudioSession? {
        return sessions.find { it.guild.id == guildId }
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (!event.isFromGuild()) return
        if (event.getAuthor().isBot()) return
        val msg_content = event.message.contentRaw

        if (!msg_content.startsWith("_"))
            return

        // Skip discord underline message
        if (msg_content.startsWith("_") && msg_content.endsWith("_"))
            return

        val current_session = getSession(event.guild.id)

        println("QUERY: [${event.message.author.id}](${event.message.author.name}) " + msg_content)

        get_conf().server_custom_conf.find {
            it.server_id == event.guild.id && it.delete_query_message
        }?.let { event.message.delete().queue() }

        if (current_session == null && (!msg_content.startsWith("_play") && !msg_content.startsWith("_help"))){
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

                if (current_session != null) {
                    if (!current_session.audioManager.isConnected)
                        current_session.connect(user_audio_channel, event.channel)

                    current_session.play(event)
                } else {
                    AudioSession(event).run {
                        sessions.add(this)
                        connect(user_audio_channel, event.channel)
                        play(event)
                    }
                }
            }
            "_skip" -> {
                if (current_session?.audioPlayer?.playingTrack == null && current_session?.trackManager?.queue?.isEmpty() == true) {
                    event.channel.sendMessage("Aucune bande son à passer.").queue()
                    return
                }
                if (msg_content.contains("--all")) {
                    current_session?.trackManager?.clearQueue(current_session.audioPlayer, true)
                    event.channel.sendMessage("Toutes les bandes son ont été passées.").queue()
                    return
                }
                val args = msg_content.split(" ")

                val numberToSkip = if (args.size > 1) args[1].toIntOrNull() else null

                if (numberToSkip == null || numberToSkip < 1)
                    event.channel.sendMessage("Bande son ${current_session?.trackManager?.skip(current_session.audioPlayer)} passer.").queue()
                else
                    event.channel.sendMessage("${current_session?.trackManager?.skip(current_session.audioPlayer, numberToSkip)} bandes son passer.").queue()
            }
            "_clear" -> {
                current_session?.trackManager?.clearQueue(current_session.audioPlayer, msg_content.contains("--all"))
                event.channel.sendMessage("La file d'attente a été vidée${if (msg_content.contains("--all")) " et la bande son actuelle supprimée." else "."}").queue()
            }
            "_list" -> {
                if (msg_content.contains("--delete|-d".toRegex())) {
                    if (current_session == null) return
                    val args = msg_content.split("--delete|-d".toRegex())
                    val numberToDelete = if (args.size > 1) args[1].trim().toIntOrNull() else null
                    println("delete $numberToDelete with queue size = ${current_session.trackManager.queue.size}")
                    if (numberToDelete != null) {
                        if (numberToDelete > 0 && numberToDelete <= current_session.trackManager.queue.size) {
                            val trackToDelete = current_session.trackManager.queue.elementAt(numberToDelete -1)
                            event.channel.sendMessage(
                                "La bande son ${format_track_title(trackToDelete)} a été retirée de la file d'attente.")
                                .queue()
                            current_session.trackManager.queue.remove(trackToDelete)
                        }
                    }
                    return
                }
                event.channel.sendMessage(list(current_session)).queue()
            }
            "_chut" -> {
                if (current_session?.audioPlayer?.isPaused == true)
                    event.channel.sendMessage("Reprise de la lecture de la bande son actuelle.").queue()
                else
                    event.channel.sendMessage("Arrêt de la lecture de la bande son actuelle.").queue()
                current_session?.audioPlayer?.isPaused = !current_session.audioPlayer.isPaused
            }
            "_leave" -> {
                getSession(event.guild.id)?.let {
                    leave_and_close_audio_session(it, "Fermeture de la session audio.", event.channel)
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

    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        if (!event.isFromGuild) return

        var allow_interaction = false
        event.member?.voiceState?.channel?.members?.forEach {
            if (it.id == event.jda.selfUser.id) allow_interaction = true
        }
        if (!allow_interaction) {
            event.reply("Vous devez être connecté au même salon vocal que le bot pour utiliser les boutons.").setEphemeral(true).queue()
            return
        }

        val current_session = getSession(event.guild!!.id)

        if (current_session == null) {
            event.reply("Aucune session de musique n'est en cours.\n**Utilisez _help pour obtenir de l'aide.**").setEphemeral(true).queue()
            return
        }

        when (event.componentId) {
            "skip" -> {
                current_session.trackManager.skip(current_session.audioPlayer)
            }
            "clear" -> {
                current_session.trackManager.clearQueue(current_session.audioPlayer, true)
            }
            "chut" -> {
                current_session.audioPlayer?.isPaused = !current_session.audioPlayer.isPaused
            }
            "leave" -> {
                leave_and_close_audio_session(current_session, "Fermeture de la session ${event.guild?.id} avec bouton.", null)
            }
            "reset" -> {
                current_session.audioPlayer?.playingTrack?.position = 0
            }
            "repeat" -> {
                current_session.trackManager.addToQueue(
                    current_session.audioPlayer?.playingTrack!!.makeClone(),
                    current_session.audioPlayer,
                    true)
            }
            "shuffle" -> {
                current_session.trackManager.queue = LinkedBlockingDeque(current_session.trackManager.queue.shuffled())
            }
        }

        event.editMessage(
            MessageEditBuilder()
                .setContent(build_list_msg(current_session))
                .setComponents(build_list_component(current_session))
                .build()
        ).queue()
    }

    override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
        if (event.channelLeft != null) {
            if (event.channelLeft?.members?.size == 1) {
                if (event.channelLeft?.members?.first()?.id == event.jda.selfUser.id) {
                    getSession(event.guild.id)?.let {
                        leave_and_close_audio_session(it, "Fermeture de la session ${event.guild.id}.", null)
                    }
                }
            }
            if (event.entity.id == event.jda.selfUser.id && event.newValue == null) {
                getSession(event.guild.id)?.let {
                    leave_and_close_audio_session(it, "Fermeture de la session ${event.guild.id} due à une déconnexion.", null)
                }
            }
        }
    }

    fun list(session: AudioSession?): MessageCreateData {
        return MessageCreateBuilder()
            .setContent(build_list_msg(session))
            .addComponents(
                build_list_component(session)
            ).build()
    }

    fun build_list_msg(session: AudioSession?): String {
        if (session == null || (session.trackManager.queue.isEmpty() && session.audioPlayer?.playingTrack == null))
            return "File d'attente vide."

        val prefix = session.audioPlayer.playingTrack.let {
            "**" + format_track_title(it) + " [" + human_readable_duration(it.position) + "/" +
                    human_readable_duration(it.duration) + "]**\n"
        }

        val totalDuration = session.trackManager.queue.sumOf { it.duration }

        return session.trackManager.queue.withIndex().joinToString(separator = "", prefix = prefix)
        { (index, track) -> "$index. " + format_track_title(track) + " **[" + human_readable_duration(track.duration) + "]**\n" }
            .take(1930).let {
                it.substring(0, it.lastIndexOf('\n'))
            } + "\n. . .\n**__${session.trackManager.queue.size} bandes son au total dans la file d'attente__ [${human_readable_duration(totalDuration)}]**"
    }

    fun build_list_component(session: AudioSession?): List<ActionRow> {
        return listOf(
            ActionRow.of(
                Button.success("chut", Emoji.fromUnicode(if (session?.audioPlayer?.isPaused == true) "▶\uFE0F" else "⏸\uFE0F")),
                Button.primary("reset", Emoji.fromUnicode("⏪")),
                Button.primary("repeat", Emoji.fromFormatted("\uD83D\uDD02")),
                Button.danger("skip", Emoji.fromFormatted("⏭\uFE0F"))
            ),
            ActionRow.of(
                Button.success("list", Emoji.fromUnicode("\uD83D\uDD04")),
                Button.secondary("danger", Emoji.fromFormatted("⚠\uFE0F")),
                Button.danger("shuffle", Emoji.fromUnicode("\uD83D\uDD00")),
                Button.danger("clear", "clear --all"),
                Button.danger("leave", Emoji.fromFormatted("\uD83D\uDC4B"))
            )
        )
    }

    fun leave_and_close_audio_session(session: AudioSession?, log_message: String, channel: MessageChannel?) {
        session?.let {
            it.audioManager.closeAudioConnection()
            it.trackManager.clearQueue(it.audioPlayer, true)
            sessions.remove(it)
            println(log_message)
            channel?.sendMessage("Fermeture de la session audio.")?.queue()
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