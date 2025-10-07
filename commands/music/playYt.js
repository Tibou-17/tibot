const { SlashCommandBuilder } = require('discord.js');
const { getVoiceConnection, joinVoiceChannel } = require('@discordjs/voice');
const { YoutubeiExtractor } = require("discord-player-youtubei");
const { getPlayer } = require('../../player');
const player = getPlayer();

module.exports = {
    data: new SlashCommandBuilder()
        .setName('playyt')
        .setDescription('Joue une musique depuis YouTube.')
        .addStringOption(option =>
            option.setName('url')
                .setDescription('URL de la vidéo YouTube')
                .setRequired(true)),
    execute: async function(interaction) {
        const voiceChannel = interaction.member.voice.channel;
        if (!voiceChannel) {
            return interaction.reply('Vous devez être dans un salon vocal.');
        }

        const url = interaction.options.getString('url');
        if (!url) {
            return interaction.reply('Aucune URL reçue.');
        }

        // Connexion au salon vocal
        const guild = interaction.guild;
        let connection = getVoiceConnection(guild.id);
        if (!connection) {
            connection = joinVoiceChannel({
                channelId: voiceChannel.id,
                guildId: guild.id,
                adapterCreator: guild.voiceAdapterCreator,
                selfDeaf: false
            });
        }

        try {
            // Lecture de la musique via discord-player
            const queue = player.nodes.create(guild, {
                metadata: {
                    channel: interaction.channel
                },
                leaveOnEnd: true,
                leaveOnStop: true,
                leaveOnEmpty: true,
            });
            await queue.connect(voiceChannel);
            const track = await player.search(url, {
                requestedBy: interaction.user
            }).then(x => x.tracks[0]);
            if (!track) return interaction.reply('Aucune piste trouvée pour cette URL.');
            await queue.addTrack(track);
            if (!queue.node.isPlaying()) await queue.node.play();
            await interaction.reply(`Lecture de la musique depuis YouTube !`);
        } catch (error) {
            console.error(error);
            return interaction.reply('Erreur lors de la lecture de la musique.');
        }
    },
}
