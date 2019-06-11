package me.egg82.altfinder.events;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Consumer;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.extended.CachedConfigValues;
import me.egg82.altfinder.utils.ConfigUtil;
import me.egg82.altfinder.utils.LogUtil;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.event.PostLoginEvent;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostLoginCheckHandler implements Consumer<PostLoginEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AltAPI api = AltAPI.getInstance();

    public void accept(PostLoginEvent event) {
        String ip = getIp(event.getPlayer().getAddress());
        if (ip == null || ip.isEmpty()) {
            return;
        }

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
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
        api.addPlayerData(event.getPlayer().getUniqueId(), ip, cachedConfig.getServerName());
    }

    private String getIp(InetSocketAddress address) {
        if (address == null) {
            return null;
        }

        InetAddress host = address.getAddress();
        if (host == null) {
            return null;
        }

        return host.getHostAddress();
    }
}
