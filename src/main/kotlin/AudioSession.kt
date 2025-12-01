package org.example

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import dev.lavalink.youtube.YoutubeAudioSourceManager
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.managers.AudioManager

class AudioSession(event: MessageReceivedEvent) {

    val guild: Guild = event.guild

    val audioManager: AudioManager = guild.audioManager

    val audioPlayerManager: AudioPlayerManager = DefaultAudioPlayerManager()

    val audioPlayer = audioPlayerManager.createPlayer()

    val trackManager = TrackManager()

    init {
        audioPlayerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault())
        audioPlayerManager.registerSourceManager(YoutubeAudioSourceManager())
        audioPlayerManager.registerSourceManager(HttpAudioSourceManager())
        audioPlayerManager.registerSourceManager(LocalAudioSourceManager())

        AudioSourceManagers.registerRemoteSources(audioPlayerManager)
        audioManager.sendingHandler = AudioPlayerSendHandler(audioPlayer)

        val channel: AudioChannel? = event.member?.voiceState?.channel as AudioChannel?

        if (channel == null) {
            println("User is not connected to a voice channel.")
            event.channel.sendMessage("You must be connected to a voice channel to use this command.").queue()
        } else {
            if (!guild.selfMember.hasPermission(channel, Permission.VIEW_CHANNEL))
                event.channel.sendMessage("J'ai pas la permission de voir le salon ${channel.name}.").queue()
            else if (!guild.selfMember.hasPermission(channel, Permission.VOICE_CONNECT))
                event.channel.sendMessage("J'ai pas la permission de rejoindre le salon ${channel.name}.").queue()
            else if (!guild.selfMember.hasPermission(channel, Permission.VOICE_SPEAK))
                event.channel.sendMessage("J'ai pas la permission de parler dans le salon ${channel.name}.").queue()
            else
                audioManager.openAudioConnection(channel!!)
        }

        audioPlayer.addListener(trackManager)

        play(event)
        println("Création de la session ${guild.id}")
    }

    fun play (event: MessageReceivedEvent) {
        val cmd = event.message.contentRaw
        val url = if (cmd.contains("http")) {
            cmd.split(" ")[1]
        } else {
            "ytsearch:" + cmd.split(" ").drop(1).joinToString(separator = " ")
        }

        val shuffle = cmd.contains("--random")
        val addInFirstPosition = cmd.contains("--first")

        println("""
            cmd = $cmd
            url = $url
            shuffle (random) = $shuffle
            addInFirstPosition = $addInFirstPosition
        """.trimIndent())

        if (url == "ytsearch:") {
            event.channel.sendMessage("Veuillez fournir une URL ou un texte à rechercher après _play.\n**Utilisez _help pour obtenir de l'aide.**").queue()
            return
        }

        audioPlayerManager.loadItem(url, object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) {
                println("Track loaded")
                trackManager.addToQueue(track, audioPlayer, addInFirstPosition)
            }

            override fun playlistLoaded(playlist: AudioPlaylist) {
                //println(playlist.tracks.joinToString(separator = "\n") { it.info.title })
                if (cmd.contains("--all")) {
                    trackManager.addToQueue(playlist, audioPlayer, shuffle, addInFirstPosition)
                    event.channel.sendMessage("Une liste de ${playlist.tracks.size} bande-sons à était ajouter à la file d'attente").queue()
                } else
                    trackManager.addToQueue(playlist.tracks.first(), audioPlayer, addInFirstPosition)
            }

            override fun noMatches() {
                event.channel.sendMessage("Aucune correspondance de bande-son trouvée avec le lien fourni.").queue()
                println("No match")
            }

            override fun loadFailed(throwable: FriendlyException) {
                event.channel.sendMessage("""
                    Échec du chargement :
                      - cause = ${throwable.cause}
                      - severity = ${throwable.severity}
                      - message = ${throwable.message}
                """.trimIndent()).queue()
                println("Load failed ${throwable.message} ${throwable.cause} ${throwable.severity}")
            }
        })
    }
}