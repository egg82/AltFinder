package me.egg82.altfinder.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import com.google.common.collect.ImmutableSet;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.util.*;
import me.egg82.altfinder.APIException;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.core.DataInfoContainer;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.core.PlayerInfoContainer;
import me.egg82.altfinder.services.lookup.PlayerLookup;
import me.egg82.altfinder.utils.LogUtil;
import me.egg82.altfinder.utils.ValidationUtil;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommandAlias("seen")
public class SeenCommand extends BaseCommand {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ProxyServer proxy;

    private final AltAPI api = AltAPI.getInstance();

    public SeenCommand(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Default
    @CommandPermission("altfinder.seen")
    @Description("Shows the last logout time of a player. Uses pagination to limit output.")
    @Syntax("<ip|name> [page]")
    public void onDefault(CommandSource source, String ipOrName, @Default("0") int page) {
        if (ValidationUtil.isValidIp(ipOrName)) {
            if (source.hasPermission("altfinder.seen.ip")) {
                searchIP(source, ipOrName, Math.max(0, page - 1) * 3, 3);
            } else {
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("You must have the \"altfinder.seen.ip\" permission node to search IPs.").color(TextColor.DARK_RED)).build());
            }
            return;
        }
        searchPlayer(source, ipOrName, Math.max(0, page - 1) * 3, 3);
    }

    @CatchUnknown
    @Syntax("[command]")
    public void onHelp(CommandSource source, CommandHelp help) {
        help.showHelp();
    }

    private void searchIP(CommandSource source, String ip, int start, int showNum) {
        source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Fetching players on IP ").color(TextColor.YELLOW)).append(TextComponent.of(ip).color(TextColor.WHITE)).append(TextComponent.of(", please wait..").color(TextColor.YELLOW)).build());

        Set<PlayerInfoContainer> playerInfo = new HashSet<>();
        ImmutableSet<PlayerData> data;
        try {
            data = api.getPlayerData(ip);
        } catch (APIException ex) {
            logger.error(ex.getMessage(), ex);
            source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Internal error").color(TextColor.DARK_RED)).build());
            return;
        }
        for (PlayerData d : data) {
            playerInfo.add(new PlayerInfoContainer(d).setName(getName(d.getUUID(), proxy)));
        }

        long time;
        try {
            time = api.getCurrentSQLTime();
        } catch (APIException ex) {
            logger.error(ex.getMessage(), ex);
            time = System.currentTimeMillis();
        }
        DataInfoContainer dataInfo = new DataInfoContainer(playerInfo).setSQLTime(time);

        for (PlayerInfoContainer i : dataInfo.getInfo()) {
            i.setName(getName(i.getData().getUUID(), proxy));
        }

        List<PlayerInfoContainer> sorted = new ArrayList<>(dataInfo.getInfo());
        sorted.sort((v1, v2) -> Long.compare(v1.getData().getCreated(), v2.getData().getCreated()));

        if (dataInfo.getInfo().isEmpty()) {
            source.sendMessage(LogUtil.getHeading().append(TextComponent.of("No players").color(TextColor.RED)).append(TextComponent.of(" have logged in from ").color(TextColor.YELLOW)).append(TextComponent.of(ip).color(TextColor.WHITE)).build());
        } else {
            source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Page ").color(TextColor.YELLOW)).append(TextComponent.of(start / showNum + 1).color(TextColor.WHITE)).append(TextComponent.of("/").color(TextColor.YELLOW)).append(TextComponent.of((int) Math.ceil((double) sorted.size() / (double) showNum)).color(TextColor.WHITE)).build());
            for (int i = start; i < start + showNum; i++) {
                if (i >= sorted.size()) {
                    break;
                }
                PlayerInfoContainer info = sorted.get(i);
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Player: ").color(TextColor.YELLOW)).append((info.getName() != null ? TextComponent.of(info.getName()).color(TextColor.GREEN) : TextComponent.of("UNKNOWN").color(TextColor.RED))).build());
                source.sendMessage(TextComponent.builder().append(TextComponent.of(" - First seen: ").color(TextColor.YELLOW)).append(TextComponent.of(getTime(info.getData().getCreated(), dataInfo.getSQLTime())).color(TextColor.WHITE)).append(TextComponent.of(" ago").color(TextColor.YELLOW)).build());
                source.sendMessage(TextComponent.builder().append(TextComponent.of(" - Last seen: ").color(TextColor.YELLOW)).append(TextComponent.of(getTime(info.getData().getUpdated(), dataInfo.getSQLTime())).color(TextColor.WHITE)).append(TextComponent.of(" ago on ").color(TextColor.YELLOW)).append(TextComponent.of(info.getData().getServer()).color(TextColor.WHITE)).build());
                source.sendMessage(TextComponent.builder().append(TextComponent.of(" - IP Login Count: ").color(TextColor.YELLOW)).append(TextComponent.of(info.getData().getCount()).color(TextColor.WHITE)).build());
            }
        }
    }

    private void searchPlayer(CommandSource source, String player, int start, int showNum) {
        source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Fetching player data for ").color(TextColor.YELLOW)).append(TextComponent.of(player).color(TextColor.WHITE)).append(TextComponent.of(", please wait..").color(TextColor.YELLOW)).build());

        UUID uuid = getUuid(player, proxy);
        if (uuid == null) {
            source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Could not get UUID for ").color(TextColor.DARK_RED)).append(TextComponent.of(player).color(TextColor.WHITE)).append(TextComponent.of(" (rate-limited?)").color(TextColor.DARK_RED)).build());
            return;
        }

        Set<PlayerInfoContainer> playerInfo = new HashSet<>();
        ImmutableSet<PlayerData> data;
        try {
            data = api.getPlayerData(uuid);
        } catch (APIException ex) {
            logger.error(ex.getMessage(), ex);
            source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Internal error").color(TextColor.DARK_RED)).build());
            return;
        }
        for (PlayerData d : data) {
            playerInfo.add(new PlayerInfoContainer(d));
        }

        long time;
        try {
            time = api.getCurrentSQLTime();
        } catch (APIException ex) {
            logger.error(ex.getMessage(), ex);
            time = System.currentTimeMillis();
        }
        DataInfoContainer dataInfo = new DataInfoContainer(playerInfo).setSQLTime(time);

        for (PlayerInfoContainer i : dataInfo.getInfo()) {
            i.setName(getName(i.getData().getUUID(), proxy));
        }

        PlayerInfoContainer latest = null;
        for (PlayerInfoContainer info : dataInfo.getInfo()) {
            if (latest == null || info.getData().getUpdated() > latest.getData().getUpdated()) {
                latest = info;
            }
        }

        List<PlayerInfoContainer> sorted = new ArrayList<>(dataInfo.getInfo());
        sorted.sort((v1, v2) -> Long.compare(v1.getData().getCreated(), v2.getData().getCreated()));

        if (latest == null) {
            source.sendMessage(LogUtil.getHeading().append(TextComponent.of(player).color(TextColor.WHITE)).append(TextComponent.of(" seems to have ").color(TextColor.YELLOW)).append(TextComponent.of("never").color(TextColor.RED)).append(TextComponent.of(" logged in.").color(TextColor.YELLOW)).build());
        } else {
            if (start == 0) {
                if (proxy.getPlayer(latest.getData().getUUID()).isPresent()) {
                    source.sendMessage(LogUtil.getHeading().append(TextComponent.of(player).color(TextColor.WHITE)).append(TextComponent.of(" is currently ").color(TextColor.YELLOW)).append(TextComponent.of("online").color(TextColor.GREEN)).append(TextComponent.of(" on ").color(TextColor.YELLOW)).append(TextComponent.of("this server").color(TextColor.WHITE)).append(TextComponent.of(".").color(TextColor.YELLOW)).build());
                } else {
                    source.sendMessage(LogUtil.getHeading().append(TextComponent.of(player).color(TextColor.WHITE)).append(TextComponent.of(" was last seen ").color(TextColor.YELLOW)).append(TextComponent.of(getTime(latest.getData().getUpdated(), dataInfo.getSQLTime())).color(TextColor.WHITE)).append(TextComponent.of(" ago on ").color(TextColor.YELLOW)).append(TextComponent.of(latest.getData().getServer()).color(TextColor.WHITE)).build());
                }
            }
            source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Page ").color(TextColor.YELLOW)).append(TextComponent.of(start / showNum + 1).color(TextColor.WHITE)).append(TextComponent.of("/").color(TextColor.YELLOW)).append(TextComponent.of((int) Math.ceil((double) sorted.size() / (double) showNum)).color(TextColor.WHITE)).build());
            for (int i = start; i < start + showNum; i++) {
                if (i >= sorted.size()) {
                    break;
                }
                PlayerInfoContainer info = sorted.get(i);
                if (source.hasPermission("altfinder.seen.ip")) {
                    source.sendMessage(LogUtil.getHeading().append(TextComponent.of("IP: ").color(TextColor.YELLOW)).append(TextComponent.of(info.getData().getIP()).color(TextColor.WHITE)).build());
                } else {
                    source.sendMessage(LogUtil.getHeading().append(TextComponent.of("IP: ").color(TextColor.YELLOW)).append(TextComponent.of("REDACTED").color(TextColor.WHITE).decoration(TextDecoration.STRIKETHROUGH, true)).build());
                }
                source.sendMessage(TextComponent.builder().append(TextComponent.of(" - First seen: ").color(TextColor.YELLOW)).append(TextComponent.of(getTime(info.getData().getCreated(), dataInfo.getSQLTime())).color(TextColor.WHITE)).append(TextComponent.of(" ago").color(TextColor.YELLOW)).build());
                source.sendMessage(TextComponent.builder().append(TextComponent.of(" - Last seen: ").color(TextColor.YELLOW)).append(TextComponent.of(getTime(info.getData().getUpdated(), dataInfo.getSQLTime())).color(TextColor.WHITE)).append(TextComponent.of(" ago on ").color(TextColor.YELLOW)).append(TextComponent.of(info.getData().getServer()).color(TextColor.WHITE)).build());
                source.sendMessage(TextComponent.builder().append(TextComponent.of(" - IP Login Count: ").color(TextColor.YELLOW)).append(TextComponent.of(info.getData().getCount()).color(TextColor.WHITE)).build());
            }
            if (!source.hasPermission("altfinder.seen.ip")) {
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("You must have the \"altfinder.seen.ip\" permission node to see redacted information.").color(TextColor.YELLOW)).build());
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

    private String getTime(long time, long current) {
        long newTime = current - time;
        return DurationFormatUtils.formatDurationWords(newTime, true, true);
    }
}
