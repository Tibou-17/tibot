package org.example

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import java.util.concurrent.LinkedBlockingDeque

class TrackManager(): AudioEventAdapter() {
    var queue: LinkedBlockingDeque<AudioTrack> = LinkedBlockingDeque()

    private fun playNextTrack(audioPlayer: AudioPlayer, noInterrupt: Boolean){

        val nextTrack = queue.poll()

        if(nextTrack == null){
            audioPlayer.stopTrack()
        } else{
            if(audioPlayer.startTrack(nextTrack, noInterrupt)){
                println("Lecture de la bande sonnore ${nextTrack.info.title}")
            } else {
                println("Erreur de lecture de la bande sonnore")
            }
        }
    }

    fun addToQueue(track: AudioTrack, audioPlayer: AudioPlayer, addFirst: Boolean = false){
        if (addFirst) queue.addFirst(track) else queue.addLast(track)
        println("Bande sonnore ${track.info.title} ajouter à la file d'attente")
        if(audioPlayer.playingTrack == null)
            playNextTrack(audioPlayer, true)
    }

    fun addToQueue(playlist: AudioPlaylist, audioPlayer: AudioPlayer, shuffle: Boolean = false, addFirst: Boolean = false) {
        if (shuffle) playlist.tracks.shuffle()

        for (track in playlist.tracks) {
            if (addFirst) queue.addFirst(track) else queue.addLast(track)
        }

        println("Playliste de ${playlist.tracks.size} bande sonnore ajouter à la file d'attente." +
                if (shuffle) "\n**Cette playlist a était mélanger aléatoirement.**" else "")

        if(audioPlayer.playingTrack == null)
            playNextTrack(audioPlayer, true)
    }

    fun clearQueue(audioPlayer: AudioPlayer, clearAll: Boolean = false){
        if(queue.isNotEmpty() || audioPlayer.playingTrack != null){
            queue.clear()
            if (clearAll)
                audioPlayer.stopTrack()
        }
    }

    fun skip(audioPlayer: AudioPlayer, numberToSkip: Int? = null): String? {
        var returnStr: String? = ""
        if (numberToSkip != null && numberToSkip > 1) {
            if (numberToSkip > 100) {
                returnStr = "100"
                repeat(99) {
                    queue.poll()
                }
            } else {
                returnStr = numberToSkip.toString()
                repeat(numberToSkip - 1) {
                    queue.poll()
                }
            }
        }
        if (returnStr == "") returnStr = audioPlayer.playingTrack?.info?.title
        playNextTrack(audioPlayer, false)
        return returnStr
    }

    override fun onTrackEnd(audioPlayer: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        println("Track ${track.info.title} END")
        if(endReason.mayStartNext){
            playNextTrack(audioPlayer, true)
        }
    }

    override fun onTrackException(player: AudioPlayer, track: AudioTrack, exception: FriendlyException) {
        println("Trackmanager: Track Exception ${exception.message} ${exception.cause} ${exception.severity}")
    }
}