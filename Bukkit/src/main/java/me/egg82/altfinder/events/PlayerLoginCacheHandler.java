package me.egg82.altfinder.events;

import java.net.InetAddress;
import java.util.Optional;
import java.util.function.Consumer;
import me.egg82.altfinder.APIException;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.extended.CachedConfigValues;
import me.egg82.altfinder.utils.ConfigUtil;
import me.egg82.altfinder.utils.LogUtil;
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

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        if (cachedConfig.get().getIgnored().contains(ip)) {
            if (cachedConfig.get().getDebug()) {
                logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " is using an ignored IP " + ChatColor.WHITE + ip +  ChatColor.YELLOW + ". Ignoring.");
            }
            return;
        } else if (cachedConfig.get().getIgnored().contains(event.getPlayer().getUniqueId().toString())) {
            if (cachedConfig.get().getDebug()) {
                logger.info(LogUtil.getHeading() + ChatColor.WHITE + event.getPlayer().getName() + ChatColor.YELLOW + " is using an ignored UUID. Ignoring.");
            }
            return;
        }

        if (cachedConfig.get().getDebug()) {
            logger.info(LogUtil.getHeading() + ChatColor.YELLOW + "Logging UUID " + ChatColor.WHITE + event.getPlayer().getUniqueId() + ChatColor.YELLOW + " with IP " + ChatColor.WHITE + ip +  ChatColor.YELLOW + ".");
        }

        try {
            api.addPlayerData(event.getPlayer().getUniqueId(), ip, cachedConfig.get().getServerName());
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
