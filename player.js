const { Player } = require('discord-player');
let player = null;

function createPlayer(client) {
    player = new Player(client);
    return player;
}

function getPlayer() {
    return player;
}

module.exports = { createPlayer, getPlayer };
