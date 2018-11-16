package me.egg82.altfinder.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.CommandManager;
import co.aikar.commands.annotation.*;
import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainFactory;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.*;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.services.lookup.PlayerLookup;
import me.egg82.altfinder.utils.LogUtil;
import me.egg82.altfinder.utils.ValidationUtil;
import ninja.egg82.tuples.objects.ObjectLongPair;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CommandAlias("seen")
public class SeenCommand extends BaseCommand {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CommandManager manager;
    private final TaskChainFactory taskFactory;

    private final AltAPI api = AltAPI.getInstance();

    public SeenCommand(CommandManager manager, TaskChainFactory taskFactory) {
        this.manager = manager;
        this.taskFactory = taskFactory;
    }

    @CatchUnknown @Default
    @CommandPermission("altfinder.seen")
    @Description("Shows the last logout time of a player.")
    @Syntax("<ip|name>")
    public void onDefault(CommandSender sender, String[] args) {
        if (args.length == 0) {
            onHelp(sender, new CommandHelp(manager, manager.getRootCommand("seen"), manager.getCommandIssuer(sender)));
            return;
        }

        String search = args[0];
        if (ValidationUtil.isValidIp(search)) {
            searchIP(sender, search);
            return;
        }
        searchPlayer(sender, search);
    }

    @HelpCommand
    @Syntax("[command]")
    public void onHelp(CommandSender sender, CommandHelp help) {
        help.showHelp();
    }

    private void searchIP(CommandSender sender, String ip) {
        sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Fetching players on IP " + ChatColor.WHITE + ip + ChatColor.YELLOW + ", please wait..");

        TaskChain<?> chain = taskFactory.newChain();

        chain
                .<ObjectLongPair<ImmutableSet<PlayerData>>>asyncFirstCallback(f -> {
                    ImmutableSet<PlayerData> data = api.getPlayerData(ip);
                    // This is just here so the names are cached before going into sync
                    for (PlayerData d : data) {
                        getName(d.getUUID());
                    }
                    f.accept(new ObjectLongPair<>(data, api.getCurrentSQLTime()));
                })
                .syncLast(v -> {
                    List<PlayerData> sorted = new ArrayList<>(v.getFirst());
                    sorted.sort(Comparator.comparingLong(PlayerData::getCreated));

                    if (v.getFirst().isEmpty()) {
                        sender.sendMessage(LogUtil.getHeading() + ChatColor.RED + "No players" + ChatColor.YELLOW + " have logged in from " + ChatColor.WHITE + ip);
                    } else {
                        for (PlayerData data : sorted) {
                            String name = getName(data.getUUID());
                            sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Player: " + (name != null ? ChatColor.GREEN + name : ChatColor.RED + "UNKNOWN"));
                            sender.sendMessage(ChatColor.YELLOW + " - First seen: " + ChatColor.WHITE + getTime(data.getCreated(), v.getSecond()) + ChatColor.YELLOW + " ago");
                            sender.sendMessage(ChatColor.YELLOW + " - Last seen: " + ChatColor.WHITE + getTime(data.getUpdated(), v.getSecond()) + ChatColor.YELLOW + " ago on " + ChatColor.WHITE + data.getServer());
                            sender.sendMessage(ChatColor.YELLOW + " - IP Login Count: " + ChatColor.WHITE + data.getCount());
                        }
                    }
                })
                .execute();
    }

    private void searchPlayer(CommandSender sender, String player) {
        sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Fetching player data for " + ChatColor.WHITE + player + ChatColor.YELLOW + ", please wait..");

        TaskChain<?> chain = taskFactory.newChain();

        chain
                .<ObjectLongPair<ImmutableSet<PlayerData>>>asyncFirstCallback(f -> {
                    UUID uuid = getUuid(player);
                    if (uuid == null) {
                        f.accept(null);
                        return;
                    }

                    ImmutableSet<PlayerData> data = api.getPlayerData(uuid);
                    // This is just here so the names are cached before going into sync
                    for (PlayerData d : data) {
                        getName(d.getUUID());
                    }
                    f.accept(new ObjectLongPair<>(data, api.getCurrentSQLTime()));
                })
                .syncLast(v -> {
                    if (v == null) {
                        sender.sendMessage(ChatColor.DARK_RED + "Could not get UUID for " + ChatColor.WHITE + player + ChatColor.DARK_RED + " (rate-limited?)");
                        return;
                    }

                    PlayerData latest = null;
                    for (PlayerData data : v.getFirst()) {
                        if (latest == null || data.getUpdated() > latest.getUpdated()) {
                            latest = data;
                        }
                    }

                    List<PlayerData> sorted = new ArrayList<>(v.getFirst());
                    sorted.sort(Comparator.comparingLong(PlayerData::getCreated));

                    if (latest == null) {
                        sender.sendMessage(LogUtil.getHeading() + ChatColor.WHITE + player + ChatColor.YELLOW + " seems to have " + ChatColor.RED + "never" + ChatColor.YELLOW + " logged in.");
                    } else {
                        if (Bukkit.getPlayer(latest.getUUID()) != null) {
                            sender.sendMessage(LogUtil.getHeading() + ChatColor.WHITE + player + ChatColor.YELLOW + " is currently " + ChatColor.GREEN + "online" + ChatColor.YELLOW + " on " + ChatColor.WHITE + "this server" + ChatColor.YELLOW + ".");
                        } else {
                            sender.sendMessage(LogUtil.getHeading() + ChatColor.WHITE + player + ChatColor.YELLOW + " was last seen " + ChatColor.WHITE + getTime(latest.getUpdated(), v.getSecond()) + ChatColor.YELLOW + " ago on " + ChatColor.WHITE + latest.getServer());
                        }
                        for (PlayerData data : sorted) {
                            sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "IP: " + ChatColor.WHITE + data.getIP());
                            sender.sendMessage(ChatColor.YELLOW + " - First seen: " + ChatColor.WHITE + getTime(data.getCreated(), v.getSecond()) + ChatColor.YELLOW + " ago");
                            sender.sendMessage(ChatColor.YELLOW + " - Last seen: " + ChatColor.WHITE + getTime(data.getUpdated(), v.getSecond()) + ChatColor.YELLOW + " ago on " + ChatColor.WHITE + data.getServer());
                            sender.sendMessage(ChatColor.YELLOW + " - IP Login Count: " + ChatColor.WHITE + data.getCount());
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
