package me.egg82.altfinder.commands.internal;

import java.io.IOException;
import java.util.UUID;
import me.egg82.altfinder.APIException;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.services.lookup.PlayerLookup;
import me.egg82.altfinder.utils.LogUtil;
import me.egg82.altfinder.utils.ValidationUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CommandSender sender;
    private final String search;

    private final AltAPI api = AltAPI.getInstance();

    public DeleteCommand(CommandSender sender, String search) {
        this.sender = sender;
        this.search = search;
    }

    public void run() {
        if (ValidationUtil.isValidIp(search)) {
            sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.YELLOW + "Deleting IP " + ChatColor.WHITE + search + ChatColor.YELLOW + ", please wait.."));

            try {
                api.removePlayerData(search);
                sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.GREEN + "IP successfully removed!"));
            } catch (APIException ex) {
                logger.error(ex.getMessage(), ex);
                sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.DARK_RED + "Internal error"));
            }
        } else {
            sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.YELLOW + "Deleting player " + ChatColor.WHITE + search + ChatColor.YELLOW + ", please wait.."));

            UUID uuid = getUuid(search);
            if (uuid == null) {
                sender.sendMessage(new TextComponent(ChatColor.DARK_RED + "Could not get UUID for " + ChatColor.WHITE + search + ChatColor.DARK_RED + " (rate-limited?)"));
                return;
            }

            try {
                api.removePlayerData(uuid);
                sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.GREEN + "Player successfully removed!"));
            } catch (APIException ex) {
                logger.error(ex.getMessage(), ex);
                sender.sendMessage(new TextComponent(LogUtil.getHeading() + ChatColor.DARK_RED + "Internal error"));
            }
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
