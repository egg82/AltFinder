package me.egg82.altfinder.commands.internal;

import com.google.common.collect.ImmutableSet;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.util.*;
import me.egg82.altfinder.APIException;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.core.PlayerInfoContainer;
import me.egg82.altfinder.services.lookup.PlayerLookup;
import me.egg82.altfinder.utils.LogUtil;
import me.egg82.altfinder.utils.ValidationUtil;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CommandSource source;
    private final String search;
    private final ProxyServer proxy;

    private final AltAPI api = AltAPI.getInstance();

    public SearchCommand(CommandSource source, String search, ProxyServer proxy) {
        this.source = source;
        this.search = search;
        this.proxy = proxy;
    }

    public void run() {
        if (ValidationUtil.isValidIp(search)) {
            source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Fetching players on IP ").color(TextColor.YELLOW)).append(TextComponent.of(search).color(TextColor.WHITE)).append(TextComponent.of(", please wait..").color(TextColor.YELLOW)).build());

            Set<PlayerInfoContainer> playerInfo = new HashSet<>();
            ImmutableSet<PlayerData> data;
            try {
                data = api.getPlayerData(search);
            } catch (APIException ex) {
                logger.error(ex.getMessage(), ex);
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Internal error").color(TextColor.DARK_RED)).build());
                return;
            }
            for (PlayerData d : data) {
                playerInfo.add(new PlayerInfoContainer(d).setName(getName(d.getUUID(), proxy)));
            }

            for (PlayerInfoContainer i : playerInfo) {
                i.setName(getName(i.getData().getUUID(), proxy));
            }

            List<PlayerInfoContainer> sorted = new ArrayList<>(playerInfo);
            sorted.sort((v1, v2) -> Long.compare(v2.getData().getCreated(), v1.getData().getCreated()));

            TextComponent.Builder players = TextComponent.builder();
            for (int i = 0; i < sorted.size(); i++) {
                PlayerInfoContainer info = sorted.get(i);
                players.append(info.getName() != null ? TextComponent.of(info.getName()).color(TextColor.GREEN) : TextComponent.of("UNKNOWN").color(TextColor.RED));
                if (i < sorted.size() - 1) {
                    players.append(TextComponent.of(", ").color(TextColor.YELLOW));
                }
            }

            if (sorted.isEmpty()) {
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("No players found.").color(TextColor.RED)).build());
            } else {
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Players: ").color(TextColor.YELLOW)).append(players.build()).build());
            }
        } else {
            source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Finding alts for player ").color(TextColor.YELLOW)).append(TextComponent.of(search).color(TextColor.WHITE)).append(TextComponent.of(", please wait..").color(TextColor.YELLOW)).build());

            UUID uuid = getUuid(search, proxy);
            if (uuid == null) {
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Could not get UUID for ").color(TextColor.DARK_RED)).append(TextComponent.of(search).color(TextColor.WHITE)).append(TextComponent.of(" (rate-limited?)").color(TextColor.DARK_RED)).build());
                return;
            }

            ImmutableSet<PlayerData> playerData;
            try {
                playerData = api.getPlayerData(uuid);
            } catch (APIException ex) {
                logger.error(ex.getMessage(), ex);
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Internal error").color(TextColor.DARK_RED)).build());
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
                i.setName(getName(i.getData().getUUID(), proxy));
            }

            List<PlayerInfoContainer> sorted = new ArrayList<>(altInfo);
            sorted.sort((v1, v2) -> Long.compare(v2.getData().getCount(), v1.getData().getCount()));

            TextComponent.Builder alts = TextComponent.builder();
            for (int i = 0; i < sorted.size(); i++) {
                PlayerInfoContainer info = sorted.get(i);
                alts.append(info.getName() != null ? TextComponent.of(info.getName()).color(TextColor.GREEN) : TextComponent.of("UNKNOWN").color(TextColor.RED));
                if (i < sorted.size() - 1) {
                    alts.append(TextComponent.of(", ").color(TextColor.YELLOW));
                }
            }

            if (sorted.isEmpty()) {
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("No potential alts found.").color(TextColor.RED)).build());
            } else {
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Potential alts: ").color(TextColor.YELLOW)).append(alts.build()).build());
            }
        }
    }

    private String getName(UUID uuid, ProxyServer proxy) {
        try {
            return PlayerLookup.get(uuid, proxy).getName();
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            return null;
        }
    }

    private UUID getUuid(String name, ProxyServer proxy) {
        try {
            return PlayerLookup.get(name, proxy).getUUID();
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            return null;
        }
    }
}
