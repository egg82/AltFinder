package me.egg82.altfinder.events;

import java.net.InetAddress;
import java.util.function.Consumer;
import me.egg82.altfinder.APIException;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.extended.CachedConfigValues;
import me.egg82.altfinder.utils.LogUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.bukkit.ChatColor;
import org.bukkit.event.player.PlayerLoginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerLoginCacheHandler implements Consumer<PlayerLoginEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AltAPI api = AltAPI.getInstance();

    public void accept(PlayerLoginEvent event) {
        String ip = getIp(event.getAddress());
        if (ip == null || ip.isEmpty()) {
            return;
        }

        CachedConfigValues cachedConfig;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (InstantiationException | IllegalAccessException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        if (cachedConfig.getIgnored().contains(ip)) {
            if (cachedConfig.getDebug()) {
                logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " is using an ignored IP " + ChatColor.WHITE + ip +  ChatColor.YELLOW + ". Ignoring.");
            }
            return;
        } else if (cachedConfig.getIgnored().contains(event.getPlayer().getUniqueId().toString())) {
            if (cachedConfig.getDebug()) {
                logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " is using an ignored UUID. Ignoring.");
            }
            return;
        }

        if (cachedConfig.getDebug()) {
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Logging UUID " + ChatColor.WHITE + event.getPlayer().getUniqueId() + ChatColor.YELLOW + " with IP " + ChatColor.WHITE + ip +  ChatColor.YELLOW + ".");
        }

        try {
            api.addPlayerData(event.getPlayer().getUniqueId(), ip, cachedConfig.getServerName());
        } catch (APIException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    private String getIp(InetAddress address) {
        if (address == null) {
            return null;
        }

        return address.getHostAddress();
    }
}
