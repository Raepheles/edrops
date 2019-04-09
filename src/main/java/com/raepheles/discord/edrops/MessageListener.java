package com.raepheles.discord.edrops;

import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This is where bot listens to the messages sent to channels it can see.
 * Since it's a simple bot I didn't make a command interface and I simply check the message
 * for commands.
 */
public class MessageListener extends ListenerAdapter {

    // For title of the embed
    private static final String BATTLE_STRING = "https://%s.e-sim.org/battle.html?id=%s";
    // Cache folder's name
    private static final String CACHE_FOLDER_NAME = "cached battles";
    // Okhttp client. I tried using it instead of java's http connection to compare
    // performance. It's slightly better.
    private OkHttpClient client = new OkHttpClient();
    // Thread Pool to make api connections asynchronously
    private ExecutorService executorService = Executors.newCachedThreadPool();
    // Total hit count in the battle
    private AtomicInteger atomicHitCount = new AtomicInteger(0);
    // Error flag for async api connections
    private AtomicBoolean error = new AtomicBoolean(false);
    // Error text in case an error occurs during async connections
    private AtomicReference<String> errorText = new AtomicReference<>("");

    // Amount of hits needed for equipments to drop
    private static final int dropQ1 = 300;
    private static final int dropQ2 = 1000;
    private static final int dropQ3 = 3000;
    private static final int dropQ4 = 10000;
    private static final int dropQ5 = 30000;
    private static final int dropSpecial = 3000;

    @Override
    public void onMessageReceived (MessageReceivedEvent event) {
        String msg = event.getMessage().getContentRaw();
        // If msg doesn't start with prefix don't proceed
        if(!msg.startsWith(Bot.getPrefix())) {
            return;
        }

        // Remove prefix from the command
        msg = msg.substring(1, msg.length());

        // Help message
        if(msg.equalsIgnoreCase("help")) {
            String help = "It's just a simple bot to calculate current drops of a battle in e-sim.\nJust use the following format:\n" +
                    "[prefix][server-name] [battle-id] [bonus]\nIt might be slow depending on my host's internet speed.\n" +
                    "Also I'm not so sure about special drops (upgrades and reshuffles). Their results might be wrong in battles with " +
                    "very high hit count.\n Use `!invite` command if you want to invite it to your server." +
                    "\n\nExamples:\n!alpha 666\n!alpha 666 120";
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("eHelper");
            eb.setThumbnail(event.getJDA().getSelfUser().getAvatarUrl());
            eb.setTimestamp(OffsetDateTime.now());
            eb.setDescription(help);
            event.getChannel().sendMessage(eb.build()).queue();
            return;
        }

        // Invite link generator
        if(msg.equalsIgnoreCase("invite")) {
            String inviteLink = event.getJDA().asBot().getInviteUrl(Arrays.asList(Permission.MESSAGE_WRITE, Permission.MESSAGE_EMBED_LINKS));
            event.getChannel().sendMessage(String.format("<%s>", inviteLink)).queue();
            return;
        }

        // Check the command
        String[] split = msg.split(" ");
        if(split.length != 2 && split.length != 3)
            return;
        // First part must be server name
        String server = split[0].toLowerCase();
        if(!Bot.getServerList().contains(server)) {
            return;
        }
        // Second part must be battle id
        String id = split[1];
        if(!StringUtil.isNumeric(id)) {
            event.getChannel().sendMessage("Battle id must be a numeric value.").queue();
            return;
        }
        // Third part (optional) must be bonus value
        double bonus = 0;
        if(split.length == 3) {
            if(!StringUtil.isNumeric(split[2])) {
                event.getChannel().sendMessage("Bonus field must be a numeric value.").queue();
                return;
            } else {
                bonus = Integer.parseInt(split[2]);
            }
        }
        // Send the results.
        try {
            event.getChannel().sendMessage(getEmbed(server, id, bonus)).queue();
        } catch(InsufficientPermissionException e) {
            System.err.println("No permission to send message");
            return;
        } catch(InterruptedException e) {
            System.err.println("Interrupted Exception");
            return;
        }
        // Log the command to log channel if the channel exists.
        String logMessage;
        if(event.getChannel().getType().equals(ChannelType.PRIVATE)) {
            logMessage = String.format("`%s (%d)` used command `%s`. Channel: `PRIVATE`.",
                    event.getAuthor().getName(),
                    event.getAuthor().getIdLong(),
                    msg);
        } else {
            logMessage = String.format("`%s (%d)` used command `%s`. Channel: `%s` | Guild: `%s (%d)`.",
                    event.getAuthor().getName(),
                    event.getAuthor().getIdLong(),
                    msg,
                    event.getChannel().getName(),
                    event.getGuild().getName(),
                    event.getGuild().getIdLong());
        }
        TextChannel logChannel = Bot.getLogChannel();
        if(logChannel != null) {
            logChannel.sendMessage(logMessage).queue();
        }
    }

    private MessageEmbed getEmbed(String server, String battleId, double bonus) throws InterruptedException {
        atomicHitCount.set(0);
        error.set(false);
        errorText.set("");
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Error");
        JSONArray array;
        // This is used the calculate the time that process takes.
        long first = System.nanoTime();
        // Make the connection to battle api (with okhttp)
        String result = getStringFromUrl(ApiLinks.getApiBattle(server, battleId));
        if(result == null) {
            eb.setDescription("Failed to establish connection to the api.");
            return eb.build();
        }
        result = result.trim();
        if(!result.startsWith("[")) {
            eb.setDescription("Failed to get battle data from api.");
            return eb.build();
        }
        array = new JSONArray(result);
        int maxRound = array.getJSONObject(0).getInt("currentRound");
        // CountDownLatch to keep track of other connection threads
        CountDownLatch workDone = new CountDownLatch(maxRound);

        // Make connections to fight api for rounds
        for(int i = 1; i <= maxRound; i++) {
            // For max round ignore cache unless cached array is empty (finished battle)
            if(i == maxRound) {
                JSONArray arr = getCache(server, battleId, String.valueOf(i));
                if(arr != null) {
                    // There is a cache
                    if(!arr.isEmpty()) {
                        // If it's not empty then battle is active make connection again.
                        Runnable runnable = getRunnable(server, battleId, String.valueOf(i), workDone);
                        executorService.submit(runnable);
                    } else {
                        // If it is empty then battle is over
                        workDone.countDown();
                    }
                } else {
                    // There is no cache so make the connection.
                    Runnable runnable = getRunnable(server, battleId, String.valueOf(i), workDone);
                    executorService.submit(runnable);
                }
            } else {
                // Check if there is a cache otherwise make connection
                JSONArray arr = getCache(server, battleId, String.valueOf(i));
                if(arr != null) {
                    calculateHits(arr);
                    workDone.countDown();
                } else {
                    Runnable runnable = getRunnable(server, battleId, String.valueOf(i), workDone);
                    executorService.submit(runnable);
                }
            }
        }
        // Wait until connections are over using the CountDownLatch
        workDone.await();
        // Check if there was an error during connections.
        if(error.get()) {
            eb.setDescription(errorText.get());
            return eb.build();
        }
        // The following code is mathematical calculations for drops
        int hitCount = atomicHitCount.get();
        int[] eqDrops = new int[5];
        int[] specialDrops = new int[2];
        int hitCountWithBonus = hitCount + (int)(hitCount * bonus/100);

        if(hitCountWithBonus > dropQ1/2) {
            eqDrops[0] = ((hitCountWithBonus - dropQ1/2) / dropQ1) + 1;
        }
        if(hitCountWithBonus > dropQ2/2) {
            eqDrops[1] = ((hitCountWithBonus - dropQ2/2) / dropQ2) + 1;
        }
        if(hitCountWithBonus > dropQ3/2) {
            eqDrops[2] = ((hitCountWithBonus - dropQ3/2) / dropQ3) + 1;
        }
        if(hitCountWithBonus > dropQ4/2) {
            eqDrops[3] = ((hitCountWithBonus - dropQ4/2) / dropQ4) + 1;
        }
        if(hitCountWithBonus > dropQ5/2) {
            eqDrops[4] = ((hitCountWithBonus - dropQ5/2) / dropQ5) + 1;
        }
        // I'm not certain if this is correct formula for special drops
        // It appears to give correct results most of time but for battles
        // with very high hit counts it's not accurate. I'll change it once
        // I find out the correct formula.
        if(hitCount > dropSpecial) {
            int count = hitCount/3000;
            for(int i = 1; i <= count; i++) {
                if(i % 4 == 1)
                    specialDrops[0]++;
                else
                    specialDrops[1]++;
            }
        }
        int requiredQ5;
        if(hitCountWithBonus < dropQ5/2) {
            requiredQ5 = (int)Math.ceil((dropQ5/2 - hitCountWithBonus) / ((bonus/100)+1));
        } else {
            requiredQ5 = (int)Math.ceil((dropQ5 - ((hitCountWithBonus-dropQ5/2) % dropQ5 )) / ((bonus/100)+1.0));
        }
        String reply = String.format("**Hits:** %d\n**Required hits for next q5:** %d\n\n" +
                "**Q1 drops:** %d\n**Q2 drops:** %d\n**Q3 drops:** %d\n**Q4 drops:** %d\n**Q5 drops:** %d\n\n" +
                "**Upgrades:** %d\n**Reshuffles:** %d",
                hitCount,
                requiredQ5,
                eqDrops[0],
                eqDrops[1],
                eqDrops[2],
                eqDrops[3],
                eqDrops[4],
                specialDrops[0],
                specialDrops[1]);
        String title = String.format("[%s%s] Battle %s", server.substring(0, 1).toUpperCase(),
                server.substring(1, server.length()), battleId);
        eb.setTitle(title, String.format(BATTLE_STRING, server, String.valueOf(battleId)));
        eb.setDescription(reply);
        long second = System.nanoTime();
        DecimalFormatSymbols dfs = new DecimalFormatSymbols();
        dfs.setDecimalSeparator('.');
        DecimalFormat df = new DecimalFormat("#.##", dfs);
        String time = df.format((second-first)*Math.pow(10,-9));
        eb.setFooter(String.format("Process time: %s seconds", time), null);
        return eb.build();
    }

    /**
     * Okhttp connection that returns source code of the url.
     * @param url Url of the website to connect to.
     * @return Source code of the url.
     */
    private String getStringFromUrl(String url) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        try (Response response = client.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if(responseBody == null) {
                return null;
            }
            return responseBody.string();
        } catch(IOException e) {
            return null;
        }
    }

    /**
     * Returns a runnable that connects to the game api. Used for async connections
     * @param server Server name
     * @param battleId Battle id
     * @param roundId Round id
     * @param countDownLatch CountDownLatch to keep track of the thread.
     * @return Runnable that makes connection to the server.
     */
    private Runnable getRunnable(String server, String battleId, String roundId, CountDownLatch countDownLatch) {
        final String link = ApiLinks.getApiFight(server, battleId, roundId);
        return () -> {
            String res;
            Document doc = null;
            try {
                doc = Jsoup.connect(link).get();
                // If our connection gets rate limited we get redirected to google home page
                // Here we make sure to retry connection until we actually get a json array
                // or json object from the url
                while(!doc.text().startsWith("{") &&
                        !doc.text().startsWith("["))
                    doc = Jsoup.connect(link).get();
            } catch(IOException e) {
                error.set(true);
                errorText.set("Failed to establish connection to the api.");
            }
            if(doc != null) {
                res = doc.text();
                JSONArray hits;
                res = res.trim();
                if(res.startsWith("[")) {
                    hits = new JSONArray(res);
                    cacheTheRound(hits, server, battleId, roundId);
                    for(int i = 0; i < hits.length(); i++) {
                        boolean isBerserk = hits.getJSONObject(i).getBoolean("berserk");
                        if(isBerserk)
                            atomicHitCount.addAndGet(5);
                        else
                            atomicHitCount.incrementAndGet();
                    }
                } else if(res.startsWith("{")) {
                    JSONObject obj = new JSONObject(res);
                    if(obj.keySet().contains("error")) {
                        cacheTheRound(new JSONArray(), server, battleId, roundId);
                    }
                } else {
                    error.set(true);
                    errorText.set("There was an error while trying to get certain round's data. " +
                            "There is a high possibility that connection hit rate limit. Trying again might" +
                            " solve the issue.");
                }
            }
            countDownLatch.countDown();
        };
    }

    /**
     * Used to calculate hits from cached json array. This method doesn't return
     * anything instead updates the global atomicHitCount variable.
     * @param array Json array for the cached battle round.
     */
    private void calculateHits(JSONArray array) {
        for(int i = 0; i < array.length(); i++) {
            boolean isBerserk = array.getJSONObject(i).getBoolean("berserk");
            if(isBerserk)
                atomicHitCount.addAndGet(5);
            else
                atomicHitCount.incrementAndGet();
        }
    }

    /**
     * Gets the cached battle round's json array if it exists.
     * @param server Server name
     * @param battleId Battle id
     * @param roundId Round id
     * @return JSONArray of the cached battle round, null if it's not in the cache.
     */
    private JSONArray getCache(String server, String battleId, String roundId) {
        Path roundPath = Paths.get(String.format("%s/%s/%s/%s.json", CACHE_FOLDER_NAME, server, battleId, roundId));
        if(Files.exists(roundPath)) {
            List<String> lines;
            try {
                lines = Files.readAllLines(roundPath);
                StringBuilder sb = new StringBuilder();
                for(String line: lines) {
                    sb.append(line).append("\n");
                }
                return new JSONArray(sb.toString());
            } catch(IOException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Saves the round information to cache folder.
     * @param arr JSONArray of the round
     * @param server Server name
     * @param battleId Battle id
     * @param roundId Round id
     */
    private void cacheTheRound(JSONArray arr, String server, String battleId, String roundId) {
        // Create cache folder if it doesn't exist
        Path cacheFolderPath = Paths.get(CACHE_FOLDER_NAME);
        if(!Files.exists(cacheFolderPath)) {
            try {
                Files.createDirectories(cacheFolderPath);
            } catch(IOException e) {
                return;
            }
        }

        // Create server folder if it doesn't exist
        Path serverFolderPath = Paths.get(CACHE_FOLDER_NAME + "/" + server);
        if(!Files.exists(cacheFolderPath)) {
            try {
                Files.createDirectories(serverFolderPath);
            } catch(IOException e) {
                return;
            }
        }

        // Create battle folder if it doesn't exist
        Path battlePath = Paths.get(serverFolderPath + "/" + battleId);
        if(!Files.exists(battlePath)) {
            try {
                Files.createDirectories(battlePath);
            } catch(IOException e) {
                return;
            }
        }

        String text = arr.toString();
        Path roundPath = Paths.get(battlePath + "/" + roundId + ".json");
        try {
            Files.write(roundPath, text.getBytes());
        } catch(IOException ignored) {

        }
    }

}
