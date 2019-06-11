package me.egg82.altfinder.commands.internal;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.util.UUID;
import me.egg82.altfinder.APIException;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.services.lookup.PlayerLookup;
import me.egg82.altfinder.utils.LogUtil;
import me.egg82.altfinder.utils.ValidationUtil;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeleteCommand implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final CommandSource source;
    private final String search;
    private final ProxyServer proxy;

    private final AltAPI api = AltAPI.getInstance();

    public DeleteCommand(CommandSource source, String search, ProxyServer proxy) {
        this.source = source;
        this.search = search;
        this.proxy = proxy;
    }

    public void run() {
        if (ValidationUtil.isValidIp(search)) {
            source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Deleting IP ").color(TextColor.YELLOW)).append(TextComponent.of(search).color(TextColor.WHITE)).append(TextComponent.of(", please wait..").color(TextColor.YELLOW)).build());

            try {
                api.removePlayerData(search);
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("IP successfully removed!").color(TextColor.GREEN)).build());
            } catch (APIException ex) {
                logger.error(ex.getMessage(), ex);
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Internal error").color(TextColor.DARK_RED)).build());
            }
        } else {
            source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Deleting player ").color(TextColor.YELLOW)).append(TextComponent.of(search).color(TextColor.WHITE)).append(TextComponent.of(", please wait..").color(TextColor.YELLOW)).build());

            UUID uuid = getUuid(search, proxy);
            if (uuid == null) {
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Could not get UUID for ").color(TextColor.DARK_RED)).append(TextComponent.of(search).color(TextColor.WHITE)).append(TextComponent.of(" (rate-limited?)").color(TextColor.DARK_RED)).build());
                return;
            }

            try {
                api.removePlayerData(uuid);
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Player successfully removed!").color(TextColor.GREEN)).build());
            } catch (APIException ex) {
                logger.error(ex.getMessage(), ex);
                source.sendMessage(LogUtil.getHeading().append(TextComponent.of("Internal error").color(TextColor.DARK_RED)).build());
            }
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
