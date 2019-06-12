package me.egg82.altfinder.commands.internal;

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.*;
import me.egg82.altfinder.APIException;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.core.PlayerInfoContainer;
import me.egg82.altfinder.services.lookup.PlayerLookup;
import me.egg82.altfinder.utils.LogUtil;
import me.egg82.altfinder.utils.ValidationUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CommandSender sender;
    private final String search;

    private final AltAPI api = AltAPI.getInstance();

    public SearchCommand(CommandSender sender, String search) {
        this.sender = sender;
        this.search = search;
    }

    public void run() {
        if (ValidationUtil.isValidIp(search)) {
            sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.YELLOW + "Finding players on IP " + ChatColor.WHITE + search + ChatColor.YELLOW + ", please wait.."));

            Set<PlayerInfoContainer> playerInfo = new HashSet<>();
            ImmutableSet<PlayerData> data;
            try {
                data = api.getPlayerData(search);
            } catch (APIException ex) {
                logger.error(ex.getMessage(), ex);
                sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.DARK_RED + "Internal error"));
                return;
            }
            for (PlayerData d : data) {
                playerInfo.add(new PlayerInfoContainer(d).setName(getName(d.getUUID())));
            }

            for (PlayerInfoContainer i : playerInfo) {
                i.setName(getName(i.getData().getUUID()));
            }

            List<PlayerInfoContainer> sorted = new ArrayList<>(playerInfo);
            sorted.sort((v1, v2) -> Long.compare(v2.getData().getCreated(), v1.getData().getCreated()));

            StringBuilder players = new StringBuilder();
            for (PlayerInfoContainer info : sorted) {
                players.append(info.getName() != null ? ChatColor.GREEN + info.getName() : ChatColor.RED + "UNKNOWN").append(ChatColor.YELLOW + ", ");
            }
            if (players.length() > 0) {
                players.delete(players.length() - 4, players.length());
            }

            if (players.length() == 0) {
                sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.RED + "No players found."));
            } else {
                sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.YELLOW + "Players: " + players.toString()));
            }
        } else {
            sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.YELLOW + "Finding alts for player " + ChatColor.WHITE + search + ChatColor.YELLOW + ", please wait.."));

            UUID uuid = getUuid(search);
            if (uuid == null) {
                sender.sendMessage(new TextComponent(ChatColor.DARK_RED + "Could not get UUID for " + ChatColor.WHITE + search + ChatColor.DARK_RED + " (rate-limited?)"));
                return;
            }

            ImmutableSet<PlayerData> playerData;
            try {
                playerData = api.getPlayerData(uuid);
            } catch (APIException ex) {
                logger.error(ex.getMessage(), ex);
                sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.DARK_RED + "Internal error"));
                return;
            }

            Set<PlayerData> altData = new HashSet<>(playerData);
            for (PlayerData data : playerData) {
                try {
                    altData.addAll(api.getPlayerData(data.getIP()));
                } catch (APIException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            }
            altData.removeIf(v2 -> uuid.equals(v2.getUUID()));

            Set<PlayerInfoContainer> altInfo = new HashSet<>();
            for (PlayerData data : altData) {
                altInfo.add(new PlayerInfoContainer(data));
            }

            for (PlayerInfoContainer i :altInfo) {
                i.setName(getName(i.getData().getUUID()));
            }

            List<PlayerInfoContainer> sorted = new ArrayList<>(altInfo);
            sorted.sort((v1, v2) -> Long.compare(v2.getData().getCount(), v1.getData().getCount()));

            StringBuilder alts = new StringBuilder();
            for (PlayerInfoContainer info : sorted) {
                alts.append(info.getName() != null ? ChatColor.GREEN + info.getName() : ChatColor.RED + "UNKNOWN").append(ChatColor.YELLOW + ", ");
            }
            if (alts.length() > 0) {
                alts.delete(alts.length() - 4, alts.length());
            }

            if (alts.length() == 0) {
                sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.RED + "No potential alts found."));
            } else {
                sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.YELLOW + "Potential alts: " + alts.toString()));
            }
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
