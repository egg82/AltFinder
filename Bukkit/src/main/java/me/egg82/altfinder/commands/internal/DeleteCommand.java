package me.egg82.altfinder.commands.internal;

import co.aikar.taskchain.TaskChain;
import java.io.IOException;
import java.util.UUID;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.services.lookup.PlayerLookup;
import me.egg82.altfinder.utils.LogUtil;
import me.egg82.altfinder.utils.ValidationUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TaskChain<?> chain;
    private final CommandSender sender;
    private final String search;

    private final AltAPI api = AltAPI.getInstance();

    public DeleteCommand(TaskChain<?> chain, CommandSender sender, String search) {
        this.chain = chain;
        this.sender = sender;
        this.search = search;
    }

    public void run() {
        if (ValidationUtil.isValidIp(search)) {
            sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Deleting IP " + ChatColor.WHITE + search + ChatColor.YELLOW + ", please wait..");

            chain
                    .async(() -> api.removePlayerData(search))
                    .sync(() -> sender.sendMessage(LogUtil.getHeading() + ChatColor.GREEN + "IP successfully removed!"))
                    .execute();
        } else {
            sender.sendMessage(LogUtil.getHeading() + ChatColor.YELLOW + "Deleting player " + ChatColor.WHITE + search + ChatColor.YELLOW + ", please wait..");

            chain
                    .<UUID>asyncFirstCallback(f -> {
                        UUID uuid = getUuid(search);
                        if (uuid == null) {
                            f.accept(null);
                            return;
                        }

                        api.removePlayerData(uuid);
                        f.accept(uuid);
                    })
                    .syncLast(v -> {
                        if (v == null) {
                            sender.sendMessage(ChatColor.DARK_RED + "Could not get UUID for " + ChatColor.WHITE + search + ChatColor.DARK_RED + " (rate-limited?)");
                            return;
                        }

                        sender.sendMessage(LogUtil.getHeading() + ChatColor.GREEN + "Player successfully removed!");
                    })
                    .execute();
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
