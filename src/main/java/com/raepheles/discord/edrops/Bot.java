package com.raepheles.discord.edrops;

import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.TextChannel;

import javax.security.auth.login.LoginException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


/**
 * Main class for the Bot. This is where we check config.properties and
 * connect to the bot.
 */
public class Bot {

    private static String prefix;
    private static List<String> serverList;
    private static TextChannel logChannel;

    public static void main(String[] args) throws LoginException, InterruptedException {
        String token, servers, logChannelId;
        Properties properties = new Properties();
        try(InputStream input = new FileInputStream("config.properties")) {
            properties.load(input);
            token = properties.getProperty("token", null);
            prefix = properties.getProperty("prefix", "!");
            servers = properties.getProperty("servers", null);
            logChannelId = properties.getProperty("log_channel_id", null);
        } catch(IOException e) {
            e.printStackTrace();
            return;
        }
        if(token == null) {
            System.err.println("Token value cannot be empty.");
            System.exit(1);
        }
        if(servers == null) {
            System.err.println("You must enter server names.");
            System.exit(2);
        }
        // Mind you there is no control here whether the server names are valid or not.
        // So make sure you enter the correct server names.
        String[] split = servers.split(",");
        serverList = new ArrayList<>();
        for(String aSplit : split) {
            serverList.add(aSplit.toLowerCase());
        }

        JDA jda = new JDABuilder(token)
                .addEventListener(new ReadyListener())
                .addEventListener(new MessageListener())
                .build();
        jda.awaitReady();
        // Load the log channel or set it to null for any exception caught
        try {
            logChannel = jda.getTextChannelById(logChannelId);
        } catch(Exception e) {
            logChannel = null;
        }
    }

    public static String getPrefix() {
        return prefix;
    }

    public static List<String> getServerList() {
        return serverList;
    }

    public static TextChannel getLogChannel() {
        return logChannel;
    }
}
