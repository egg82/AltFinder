package me.egg82.altfinder.events;

import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.function.Consumer;
import me.egg82.altfinder.APIException;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.extended.CachedConfigValues;
import me.egg82.altfinder.utils.ConfigUtil;
import me.egg82.altfinder.utils.LogUtil;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostLoginCacheHandler implements Consumer<PostLoginEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ProxyServer proxy;

    private final AltAPI api = AltAPI.getInstance();

    public PostLoginCacheHandler(ProxyServer proxy) {
        this.proxy = proxy;
    }

    public void accept(PostLoginEvent event) {
        String ip = getIp(event.getPlayer().getRemoteAddress());
        if (ip == null || ip.isEmpty()) {
            return;
        }

        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            return;
        }

        if (cachedConfig.get().getIgnored().contains(ip)) {
            if (cachedConfig.get().getDebug()) {
                proxy.getConsoleCommandSource().sendMessage(LogUtil.getHeading().append(TextComponent.of(event.getPlayer().getUsername()).color(TextColor.WHITE)).append(TextComponent.of(" is using an ignored IP ").color(TextColor.YELLOW)).append(TextComponent.of(ip).color(TextColor.WHITE)).append(TextComponent.of(". Ignoring.").color(TextColor.YELLOW)).build());
            }
            return;
        } else if (cachedConfig.get().getIgnored().contains(event.getPlayer().getUniqueId().toString())) {
            if (cachedConfig.get().getDebug()) {
                proxy.getConsoleCommandSource().sendMessage(LogUtil.getHeading().append(TextComponent.of(event.getPlayer().getUsername()).color(TextColor.WHITE)).append(TextComponent.of(" is using an ignored UUID. Ignoring.").color(TextColor.YELLOW)).build());
            }
            return;
        }

        if (cachedConfig.get().getDebug()) {
            proxy.getConsoleCommandSource().sendMessage(LogUtil.getHeading().append(TextComponent.of("Logging UUID ").color(TextColor.YELLOW)).append(TextComponent.of(event.getPlayer().getUniqueId().toString()).color(TextColor.WHITE)).append(TextComponent.of(" with IP ").color(TextColor.YELLOW)).append(TextComponent.of(ip).color(TextColor.WHITE)).append(TextComponent.of(".").color(TextColor.YELLOW)).build());
        }

        try {
            api.addPlayerData(event.getPlayer().getUniqueId(), ip, cachedConfig.get().getServerName());
        } catch (APIException ex) {
            logger.error(ex.getMessage(), ex);
        }
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
