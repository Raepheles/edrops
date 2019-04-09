package com.raepheles.discord.edrops;

/**
 * Helper class to get api links.
 */
public class ApiLinks {

    public static String getApiBattle(String serverName, String battleId) {
        return String.format("http://%s.e-sim.org/apiBattles.html?battleId=%s", serverName, battleId);
    }

    public static String getApiFight(String serverName, String battleId, String roundId) {
        return String.format("http://%s.e-sim.org/apiFights.html?battleId=%s&roundId=%s", serverName, battleId, roundId);
    }

}
