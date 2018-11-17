package me.egg82.altfinder.commands.internal;

import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainAbortAction;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.*;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.core.DataInfoContainer;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.core.PlayerInfoContainer;
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
                    .<Set<PlayerInfoContainer>>asyncCallback((v, f) -> {
                        Set<PlayerInfoContainer> playerInfo = new HashSet<>();
                        ImmutableSet<PlayerData> data = api.getPlayerData(search);
                        for (PlayerData d : data) {
                            playerInfo.add(new PlayerInfoContainer(d).setName(getName(d.getUUID())));
                        }
                        f.accept(playerInfo);
                    })
                    .async(v -> {
                        for (PlayerInfoContainer i : v) {
                            i.setName(getName(i.getData().getUUID()));
                        }
                        return v;
                    })
                    .syncLast(v -> {
                        List<PlayerInfoContainer> sorted = new ArrayList<>(v);
                        sorted.sort((v1, v2) -> Long.compare(v2.getData().getCreated(), v1.getData().getCreated()));

                        StringBuilder players = new StringBuilder();
                        for (PlayerInfoContainer info : sorted) {
                            players.append(info.getName() != null ? ChatColor.GREEN + info.getName() : ChatColor.RED + "UNKNOWN").append(ChatColor.YELLOW + ", ");
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
                    .<UUID>asyncCallback((v, f) -> f.accept(getUuid(search)))
                    .abortIfNull(new TaskChainAbortAction<Object, Object, Object>() {
                        @Override
                        public void onAbort(TaskChain<?> chain, Object arg1) {
                            sender.sendMessage(ChatColor.DARK_RED + "Could not get UUID for " + ChatColor.WHITE + search + ChatColor.DARK_RED + " (rate-limited?)");
                        }
                    })
                    .<Set<PlayerInfoContainer>>asyncCallback((v, f) -> {
                        ImmutableSet<PlayerData> playerData = api.getPlayerData(v);
                        Set<PlayerData> altData = new HashSet<>(playerData);
                        for (PlayerData data : playerData) {
                            altData.addAll(api.getPlayerData(data.getIP()));
                        }
                        altData.removeIf(v2 -> v.equals(v2.getUUID()));

                        Set<PlayerInfoContainer> altInfo = new HashSet<>();
                        for (PlayerData data : altData) {
                            altInfo.add(new PlayerInfoContainer(data));
                        }

                        f.accept(altInfo);
                    })
                    .async(v -> {
                        for (PlayerInfoContainer i : v) {
                            i.setName(getName(i.getData().getUUID()));
                        }
                        return v;
                    })
                    .syncLast(v -> {
                        List<PlayerInfoContainer> sorted = new ArrayList<>(v);
                        sorted.sort((v1, v2) -> Long.compare(v2.getData().getCount(), v1.getData().getCount()));

                        StringBuilder alts = new StringBuilder();
                        for (PlayerInfoContainer info : sorted) {
                            alts.append(info.getName() != null ? ChatColor.GREEN + info.getName() : ChatColor.RED + "UNKNOWN").append(ChatColor.YELLOW + ", ");
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
