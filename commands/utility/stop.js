const { SlashCommandBuilder } = require('discord.js');
const { ownerId } = require('../../config.json');

module.exports = {
    data: new SlashCommandBuilder()
        .setName('stop')
        .setDescription('Arrête le bot.'),
    async execute(interaction) {
        if (interaction.user.id !== ownerId) {
            return interaction.reply({ content: 'Seul le propriétaire du bot peut utiliser cette commande.', ephemeral: true });
        }
        await interaction.reply('Arrêt du bot...');

        // Déconnexion des salons vocaux
        const { getVoiceConnections } = require('@discordjs/voice');
        for (const connection of getVoiceConnections().values()) {
            connection.destroy();
        }

        // Déconnexion du client Discord
        await interaction.client.destroy();

        // Arrêt du processus
        process.exit(0);
    },
};
