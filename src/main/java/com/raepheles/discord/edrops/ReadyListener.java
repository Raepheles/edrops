package com.raepheles.discord.edrops;

import net.dv8tion.jda.core.entities.Game;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

/**
 * ReadyListener used for setting the bot presence only.
 */
public class ReadyListener extends ListenerAdapter {
    @Override
    public void onReady(ReadyEvent event) {
        event.getJDA().getPresence().setPresence(Game.playing(Bot.getPrefix() + "help"), true);
    }
}
