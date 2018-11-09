package me.egg82.altfinder.commands.internal;

import co.aikar.taskchain.TaskChain;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.*;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.services.lookup.PlayerLookup;
import me.egg82.altfinder.utils.LogUtil;
import me.egg82.altfinder.utils.ValidationUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskChain<?> chain;
    private final CommandSender sender;
    private final String search;

    private final AltAPI api = AltAPI.getInstance();

    public SearchCommand(TaskChain<?> chain, CommandSender sender, String search) {
        this.chain = chain;
        this.sender = sender;
        this.search = search;
    }

    public void run() {
        if (ValidationUtil.isValidIp(search)) {
            sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Finding players on IP " + ChatColor.WHITE + search + ChatColor.YELLOW + ", please wait..");

            chain
                    .<List<PlayerData>>asyncFirstCallback(f -> {
                        Set<PlayerData> ipData = new HashSet<>(api.getPlayerData(search));

                        List<PlayerData> playerSorted = new ArrayList<>(ipData);
                        playerSorted.sort(Comparator.comparingLong(PlayerData::getCount));
                        Collections.reverse(playerSorted);

                        f.accept(playerSorted);
                    })
                    .syncLast(v -> {
                        StringBuilder players = new StringBuilder();
                        for (PlayerData data : v) {
                            String name = getName(data.getUUID());
                            players.append(name != null ? ChatColor.GREEN + name : ChatColor.RED + "UNKNOWN").append(ChatColor.YELLOW + ", ");
                        }
                        if (players.length() > 0) {
                            players.delete(players.length() - 4, players.length());
                        }

                        if (players.length() == 0) {
                            sender.sendMessage(LogUtil.getHeading() + ChatColor.RED + "No players found.");
                        } else {
                            sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Players: " + players.toString());
                        }
                    })
                    .execute();
        } else {
            sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Finding alts for player " + ChatColor.WHITE + search + ChatColor.YELLOW + ", please wait..");

            chain
                    .<List<PlayerData>>asyncFirstCallback(f -> {
                        UUID uuid = getUuid(search);
                        if (uuid == null) {
                            f.accept(null);
                            return;
                        }

                        ImmutableSet<PlayerData> uuidData = api.getPlayerData(uuid);
                        Set<PlayerData> altData = new HashSet<>(uuidData);

                        for (PlayerData data : uuidData) {
                            altData.addAll(api.getPlayerData(data.getIP()));
                        }

                        altData.removeIf(v -> uuid.equals(v.getUUID()));

                        List<PlayerData> altSorted = new ArrayList<>(altData);
                        altSorted.sort(Comparator.comparingLong(PlayerData::getCount));
                        Collections.reverse(altSorted);

                        f.accept(altSorted);
                    })
                    .syncLast(v -> {
                        if (v == null) {
                            sender.sendMessage(ChatColor.DARK_RED + "Could not get UUID for " + ChatColor.WHITE + search + ChatColor.DARK_RED + " (rate-limited?)");
                            return;
                        }

                        StringBuilder alts = new StringBuilder();
                        for (PlayerData data : v) {
                            String name = getName(data.getUUID());
                            alts.append(name != null ? ChatColor.GREEN + name : ChatColor.RED + "UNKNOWN").append(ChatColor.YELLOW + ", ");
                        }
                        if (alts.length() > 0) {
                            alts.delete(alts.length() - 4, alts.length());
                        }

                        if (alts.length() == 0) {
                            sender.sendMessage(LogUtil.getHeading() + ChatColor.RED + "No potential alts found.");
                        } else {
                            sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Potential alts: " + alts.toString());
                        }
                    })
                    .execute();
        }
    }

    private String getName(UUID uuid) {
        try {
            return PlayerLookup.get(uuid).getName();
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            return null;
        }
    }

    private UUID getUuid(String name) {
        try {
            return PlayerLookup.get(name).getUUID();
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            return null;
        }
    }
}
