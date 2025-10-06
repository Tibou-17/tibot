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
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.exceptions.RateLimitedException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.requests.GatewayIntent
import javax.security.auth.login.LoginException


class MusicBot : ListenerAdapter() {
    override fun onMessageReceived(event: MessageReceivedEvent) {
        // Make sure we only respond to events that occur in a guild
        if (!event.isFromGuild()) return
        // This makes sure we only execute our code when someone sends a message with "!play"
        if (!event.getMessage().getContentRaw().startsWith("!play")) return
        // Now we want to exclude messages from bots since we want to avoid command loops in chat!
        // this will include own messages as well for bot accounts
        // if this is not a bot make sure to check if this message is sent by yourself!
        if (event.getAuthor().isBot()) return
        val guild: Guild = event.getGuild()
        // This will get the first voice channel with the name "music"
        // matching by voiceChannel.getName().equalsIgnoreCase("music")

        val channel: VoiceChannel = event.author.jda.voiceChannels.first()
        val manager: AudioManager = guild.audioManager

        val playerManager: AudioPlayerManager = DefaultAudioPlayerManager()
        AudioSourceManagers.registerRemoteSources(playerManager)

        playerManager.registerSourceManager(SoundCloudAudioSourceManager.createDefault())
        playerManager.registerSourceManager(YoutubeAudioSourceManager())
        playerManager.registerSourceManager(HttpAudioSourceManager())
        playerManager.registerSourceManager(LocalAudioSourceManager())

        // MySendHandler should be your AudioSendHandler implementation
        manager.sendingHandler = AudioPlayerSendHandler(playerManager.createPlayer())
        // Here we finally connect to the target voice channel
        // and it will automatically start pulling the audio from the MySendHandler instance
        manager.openAudioConnection(channel)

        val url = "https://www.youtube.com/watch?v=5vNM76gMxQ0&list=RDz1hEzIIuGm8&index=8"

        playerManager.loadItem(url, object : AudioLoadResultHandler {
            override fun trackLoaded(track: AudioTrack) {
                println("Track loaded")
            }

            override fun playlistLoaded(playlist: AudioPlaylist) {
                println("Playlist loaded")
            }

            override fun noMatches() {
                println("No match")
            }

            override fun loadFailed(throwable: FriendlyException) {
                println("Load failed ${throwable.message} ${throwable.cause} ${throwable.severity}")
            }
        })

    }

    companion object {
        @Throws(IllegalArgumentException::class, LoginException::class, RateLimitedException::class)
        @JvmStatic
        fun main(args: Array<String>) {
            JDABuilder.createLight("", GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_VOICE_STATES) // Use token provided as JVM argument
                .addEventListeners(MusicBot()) // Register new MusicBot instance as EventListener
                .build() // Build JDA - connect to discord
        }
    }
}