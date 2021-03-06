package me.egg82.altfinder.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainAbortAction;
import co.aikar.taskchain.TaskChainFactory;
import com.google.common.collect.ImmutableSet;
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
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommandAlias("seen")
public class SeenCommand extends BaseCommand {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskChainFactory taskFactory;

    private final AltAPI api = AltAPI.getInstance();

    public SeenCommand(TaskChainFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    @Default
    @CommandPermission("altfinder.seen")
    @Description("Shows the last logout time of a player. Uses pagination to limit output.")
    @Syntax("<ip|name> [page]")
    @CommandCompletion("@player")
    public void onSeen(CommandSender sender, String ipOrName, @Default("0") int page) {
        if (ValidationUtil.isValidIp(ipOrName)) {
            if (sender.hasPermission("altfinder.seen.ip")) {
                searchIP(sender, ipOrName, Math.max(0, page - 1) * 3, 3);
            } else {
                sender.sendMessage(LogUtil.getHeading() + ChatColor.DARK_RED + "You must have the \"altfinder.seen.ip\" permission node to search IPs.");
            }
            return;
        }
        searchPlayer(sender, ipOrName, Math.max(0, page - 1) * 3, 3);
    }

    @CatchUnknown
    @Syntax("[command]")
    public void onHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }

    private void searchIP(CommandSender sender, String ip, int start, int showNum) {
        sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Fetching players on IP " + ChatColor.WHITE + ip + ChatColor.YELLOW + ", please wait..");

        taskFactory.newChain()
                .<DataInfoContainer>asyncCallback((v, f) -> {
                    Set<PlayerInfoContainer> playerInfo = new HashSet<>();
                    ImmutableSet<PlayerData> data;
                    try {
                        data = api.getPlayerData(ip);
                    } catch (APIException ex) {
                        logger.error(ex.getMessage(), ex);
                        f.accept(null);
                        return;
                    }
                    for (PlayerData d : data) {
                        playerInfo.add(new PlayerInfoContainer(d).setName(getName(d.getUUID())));
                    }

                    long time;
                    try {
                        time = api.getCurrentSQLTime();
                    } catch (APIException ex) {
                        logger.error(ex.getMessage(), ex);
                        time = System.currentTimeMillis();
                    }
                    f.accept(new DataInfoContainer(playerInfo).setSQLTime(time));
                })
                .abortIfNull(new TaskChainAbortAction<Object, Object, Object>() {
                    @Override
                    public void onAbort(TaskChain<?> chain, Object arg1) {
                        sender.sendMessage(LogUtil.getHeading() + LogUtil.getHeading() + ChatColor.YELLOW + "Internal error");
                    }
                })
                .async(v -> {
                    for (PlayerInfoContainer i : v.getInfo()) {
                        i.setName(getName(i.getData().getUUID()));
                    }
                    return v;
                })
                .syncLast(v -> {
                    List<PlayerInfoContainer> sorted = new ArrayList<>(v.getInfo());
                    sorted.sort((v1, v2) -> Long.compare(v1.getData().getCreated(), v2.getData().getCreated()));

                    if (v.getInfo().isEmpty()) {
                        sender.sendMessage(LogUtil.getHeading() + ChatColor.RED + "No players" + ChatColor.YELLOW + " have logged in from " + ChatColor.WHITE + ip);
                    } else {
                        sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Page " + ChatColor.WHITE + (start / showNum + 1) + ChatColor.YELLOW + "/" + ChatColor.WHITE + (int) Math.ceil((double) sorted.size() / (double) showNum));
                        for (int i = start; i < start + showNum; i++) {
                            if (i >= sorted.size()) {
                                break;
                            }
                            PlayerInfoContainer info = sorted.get(i);
                            sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Player: " + (info.getName() != null ? ChatColor.GREEN + info.getName() : ChatColor.RED + "UNKNOWN"));
                            sender.sendMessage(ChatColor.YELLOW + " - First seen: " + ChatColor.WHITE + getTime(info.getData().getCreated(), v.getSQLTime()) + ChatColor.YELLOW + " ago");
                            sender.sendMessage(ChatColor.YELLOW + " - Last seen: " + ChatColor.WHITE + getTime(info.getData().getUpdated(), v.getSQLTime()) + ChatColor.YELLOW + " ago on " + ChatColor.WHITE + info.getData().getServer());
                            sender.sendMessage(ChatColor.YELLOW + " - IP Login Count: " + ChatColor.WHITE + info.getData().getCount());
                        }
                    }
                })
                .execute();
    }

    private void searchPlayer(CommandSender sender, String player, int start, int showNum) {
        sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Fetching player data for " + ChatColor.WHITE + player + ChatColor.YELLOW + ", please wait..");

        TaskChain<?> chain = taskFactory.newChain();

        chain
                .<UUID>asyncCallback((v, f) -> f.accept(getUuid(player)))
                .abortIfNull(new TaskChainAbortAction<Object, Object, Object>() {
                    @Override
                    public void onAbort(TaskChain<?> chain, Object arg1) {
                        sender.sendMessage(ChatColor.DARK_RED + "Could not get UUID for " + ChatColor.WHITE + player + ChatColor.DARK_RED + " (rate-limited?)");
                    }
                })
                .<DataInfoContainer>asyncCallback((v, f) -> {
                    Set<PlayerInfoContainer> playerInfo = new HashSet<>();
                    ImmutableSet<PlayerData> data;
                    try {
                        data = api.getPlayerData(v);
                    } catch (APIException ex) {
                        logger.error(ex.getMessage(), ex);
                        f.accept(null);
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
                    f.accept(new DataInfoContainer(playerInfo).setSQLTime(time));
                })
                .abortIfNull(new TaskChainAbortAction<Object, Object, Object>() {
                    @Override
                    public void onAbort(TaskChain<?> chain, Object arg1) {
                        sender.sendMessage(LogUtil.getHeading() + LogUtil.getHeading() + ChatColor.YELLOW + "Internal error");
                    }
                })
                .async(v -> {
                    for (PlayerInfoContainer i : v.getInfo()) {
                        i.setName(getName(i.getData().getUUID()));
                    }
                    return v;
                })
                .syncLast(v -> {
                    PlayerInfoContainer latest = null;
                    for (PlayerInfoContainer info : v.getInfo()) {
                        if (latest == null || info.getData().getUpdated() > latest.getData().getUpdated()) {
                            latest = info;
                        }
                    }

                    List<PlayerInfoContainer> sorted = new ArrayList<>(v.getInfo());
                    sorted.sort((v1, v2) -> Long.compare(v1.getData().getCreated(), v2.getData().getCreated()));

                    if (latest == null) {
                        sender.sendMessage(LogUtil.getHeading() + ChatColor.WHITE + player + ChatColor.YELLOW + " seems to have " + ChatColor.RED + "never" + ChatColor.YELLOW + " logged in.");
                    } else {
                        if (start == 0) {
                            if (Bukkit.getPlayer(latest.getData().getUUID()) != null) {
                                sender.sendMessage(LogUtil.getHeading() + ChatColor.WHITE + player + ChatColor.YELLOW + " is currently " + ChatColor.GREEN + "online" + ChatColor.YELLOW + " on " + ChatColor.WHITE + "this server" + ChatColor.YELLOW + ".");
                            } else {
                                sender.sendMessage(LogUtil.getHeading() + ChatColor.WHITE + player + ChatColor.YELLOW + " was last seen " + ChatColor.WHITE + getTime(latest.getData().getUpdated(), v.getSQLTime()) + ChatColor.YELLOW + " ago on " + ChatColor.WHITE + latest.getData().getServer());
                            }
                        }
                        sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Page " + ChatColor.WHITE + (start / showNum + 1) + ChatColor.YELLOW + "/" + ChatColor.WHITE + (int) Math.ceil((double) sorted.size() / (double) showNum));
                        for (int i = start; i < start + showNum; i++) {
                            if (i >= sorted.size()) {
                                break;
                            }
                            PlayerInfoContainer info = sorted.get(i);
                            if (sender.hasPermission("altfinder.seen.ip")) {
                                sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "IP: " + ChatColor.WHITE + info.getData().getIP());
                            } else {
                                sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "IP: " + ChatColor.WHITE + ChatColor.STRIKETHROUGH + "REDACTED");
                            }
                            sender.sendMessage(ChatColor.YELLOW + " - First seen: " + ChatColor.WHITE + getTime(info.getData().getCreated(), v.getSQLTime()) + ChatColor.YELLOW + " ago");
                            sender.sendMessage(ChatColor.YELLOW + " - Last seen: " + ChatColor.WHITE + getTime(info.getData().getUpdated(), v.getSQLTime()) + ChatColor.YELLOW + " ago on " + ChatColor.WHITE + info.getData().getServer());
                            sender.sendMessage(ChatColor.YELLOW + " - IP Login Count: " + ChatColor.WHITE + info.getData().getCount());
                        }
                        if (!sender.hasPermission("altfinder.seen.ip")) {
                            sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "You must have the \"altfinder.seen.ip\" permission node to see redacted information.");
                        }
                    }
                })
                .execute();
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

    private String getTime(long time, long current) {
        long newTime = current - time;
        return DurationFormatUtils.formatDurationWords(newTime, true, true);
    }
}
