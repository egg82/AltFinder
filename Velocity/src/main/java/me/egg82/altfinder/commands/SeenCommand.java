package me.egg82.altfinder.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.CommandManager;
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
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommandAlias("seen")
public class SeenCommand extends BaseCommand {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CommandManager manager;
    private final ProxyServer proxy;

    private final AltAPI api = AltAPI.getInstance();

    public SeenCommand(CommandManager manager, ProxyServer proxy) {
        this.manager = manager;
        this.proxy = proxy;
    }

    @CatchUnknown @Default
    @CommandPermission("altfinder.seen")
    @Description("Shows the last logout time of a player.")
    @Syntax("<ip|name>")
    public void onDefault(CommandSource source, String[] args) {
        if (args.length == 0) {
            onHelp(source, new CommandHelp(manager, manager.getRootCommand("seen"), manager.getCommandIssuer(source)));
            return;
        }

        String search = args[0];
        if (ValidationUtil.isValidIp(search)) {
            searchIP(source, search);
            return;
        }
        searchPlayer(source, search);
    }

    @HelpCommand
    @Syntax("[command]")
    public void onHelp(CommandSource source, CommandHelp help) {
        help.showHelp();
    }

    private void searchIP(CommandSource source, String ip) {
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
            for (PlayerInfoContainer info : sorted) {
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Player: ").color(TextColor.YELLOW)).append((info.getName() != null ? TextComponent.of(info.getName()).color(TextColor.GREEN) : TextComponent.of("UNKNOWN").color(TextColor.RED))).build());
                source.sendMessage(TextComponent.builder().append(TextComponent.of(" - First seen: ").color(TextColor.YELLOW)).append(TextComponent.of(getTime(info.getData().getCreated(), dataInfo.getSQLTime())).color(TextColor.WHITE)).append(TextComponent.of(" ago").color(TextColor.YELLOW)).build());
                source.sendMessage(TextComponent.builder().append(TextComponent.of(" - Last seen: ").color(TextColor.YELLOW)).append(TextComponent.of(getTime(info.getData().getUpdated(), dataInfo.getSQLTime())).color(TextColor.WHITE)).append(TextComponent.of(" ago on ").color(TextColor.YELLOW)).append(TextComponent.of(info.getData().getServer()).color(TextColor.WHITE)).build());
                source.sendMessage(TextComponent.builder().append(TextComponent.of(" - IP Login Count: ").color(TextColor.YELLOW)).append(TextComponent.of(info.getData().getCount()).color(TextColor.WHITE)).build());
            }
        }
    }

    private void searchPlayer(CommandSource source, String player) {
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
            if (proxy.getPlayer(latest.getData().getUUID()).isPresent()) {
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of(player).color(TextColor.WHITE)).append(TextComponent.of(" is currently ").color(TextColor.YELLOW)).append(TextComponent.of("online").color(TextColor.GREEN)).append(TextComponent.of(" on ").color(TextColor.YELLOW)).append(TextComponent.of("this server").color(TextColor.WHITE)).append(TextComponent.of(".").color(TextColor.YELLOW)).build());
            } else {
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of(player).color(TextColor.WHITE)).append(TextComponent.of(" was last seen ").color(TextColor.YELLOW)).append(TextComponent.of(getTime(latest.getData().getUpdated(), dataInfo.getSQLTime())).color(TextColor.WHITE)).append(TextComponent.of(" ago on ").color(TextColor.YELLOW)).append(TextComponent.of(latest.getData().getServer()).color(TextColor.WHITE)).build());
            }
            for (PlayerInfoContainer info : sorted) {
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("IP: ").color(TextColor.YELLOW)).append(TextComponent.of(info.getData().getIP()).color(TextColor.WHITE)).build());
                source.sendMessage(TextComponent.builder().append(TextComponent.of(" - First seen: ").color(TextColor.YELLOW)).append(TextComponent.of(getTime(info.getData().getCreated(), dataInfo.getSQLTime())).color(TextColor.WHITE)).append(TextComponent.of(" ago").color(TextColor.YELLOW)).build());
                source.sendMessage(TextComponent.builder().append(TextComponent.of(" - Last seen: ").color(TextColor.YELLOW)).append(TextComponent.of(getTime(info.getData().getUpdated(), dataInfo.getSQLTime())).color(TextColor.WHITE)).append(TextComponent.of(" ago on ").color(TextColor.YELLOW)).append(TextComponent.of(info.getData().getServer()).color(TextColor.WHITE)).build());
                source.sendMessage(TextComponent.builder().append(TextComponent.of(" - IP Login Count: ").color(TextColor.YELLOW)).append(TextComponent.of(info.getData().getCount()).color(TextColor.WHITE)).build());
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
