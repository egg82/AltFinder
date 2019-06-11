package me.egg82.altfinder.hooks;

import com.djrapitops.plan.api.PlanAPI;
import com.djrapitops.plan.data.element.AnalysisContainer;
import com.djrapitops.plan.data.element.InspectContainer;
import com.djrapitops.plan.data.plugin.ContainerSize;
import com.djrapitops.plan.data.plugin.PluginData;
import com.djrapitops.plan.utilities.html.icon.Color;
import com.djrapitops.plan.utilities.html.icon.Icon;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import me.egg82.altfinder.APIException;
import me.egg82.altfinder.AltAPI;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.services.lookup.PlayerLookup;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerAnalyticsHook implements PluginHook {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public PlayerAnalyticsHook() { PlanAPI.getInstance().addPluginDataSource(new Data()); }

    public void cancel() {}

    class Data extends PluginData {
        private final AltAPI api = AltAPI.getInstance();

        private Data() {
            super(ContainerSize.THIRD, "AltFinder");
            setPluginIcon(Icon.called("ban").of(Color.RED).build());
        }

        public InspectContainer getPlayerData(UUID uuid, InspectContainer container) {
            Player player = Bukkit.getPlayer(uuid);
            String ip = player != null ? getIp(player) : null;

            ImmutableSet<PlayerData> uuidData;
            ImmutableSet<PlayerData> ipData;
            try {
                uuidData = api.getPlayerData(uuid);
                ipData = ip != null && !ip.isEmpty() ? api.getPlayerData(ip) : ImmutableSet.of();
            } catch (APIException ex) {
                logger.error(ex.getMessage(), ex);
                container.addValue("AltFinder", "ERROR");
                return container;
            }

            Set<PlayerData> altData = new HashSet<>(uuidData);

            PlayerData latest = null;
            for (PlayerData data : uuidData) {
                if (latest == null || data.getUpdated() > latest.getUpdated()) {
                    latest = data;
                }
                try {
                    altData.addAll(api.getPlayerData(data.getIP()));
                } catch (APIException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            }

            altData.removeIf(v -> uuid.equals(v.getUUID()));

            List<PlayerData> uuidSorted = new ArrayList<>(uuidData);
            uuidSorted.sort(Comparator.comparingLong(PlayerData::getCreated));

            List<PlayerData> ipSorted = new ArrayList<>(ipData);
            ipSorted.sort(Comparator.comparingLong(PlayerData::getCreated));

            List<PlayerData> altSorted = new ArrayList<>(altData);
            altSorted.sort(Comparator.comparingLong(PlayerData::getCount));
            Collections.reverse(altSorted);

            StringBuilder alts = new StringBuilder();
            for (PlayerData data : altSorted) {
                String name = getName(data.getUUID());
                alts.append(name != null ? name : "**UNKNOWN**").append(", ");
            }
            if (alts.length() > 0) {
                alts.delete(alts.length() - 2, alts.length());
            }

            if (alts.length() == 0) {
                container.addValue("Potential Alts", "**NONE**");
            } else {
                container.addValue("Potential Alts", alts.toString());
            }

            long current;
            try {
                current = api.getCurrentSQLTime();
            } catch (APIException ex) {
                logger.error(ex.getMessage(), ex);
                current = System.currentTimeMillis();
            }

            if (latest == null) {
                container.addValue("Last Online", "Never");
            } else {
                if (player != null) {
                    container.addValue("Last Online", "Now");
                } else {
                    container.addValue("Last Online", getTime(latest.getUpdated(), current) + " ago");
                    container.addValue("Last Server", latest.getServer());
                }

                for (PlayerData data : uuidSorted) {
                    container.addValue("IP", data.getIP());
                    container.addValue(" - First seen", getTime(data.getCreated(), current) + " ago");
                    container.addValue(" - Last seen", getTime(data.getUpdated(), current) + " ago on " + data.getServer());
                    container.addValue(" - IP Login Count", data.getCount());
                }

                for (PlayerData data : ipSorted) {
                    String name = getName(data.getUUID());
                    container.addValue("Player", (name != null ? name : "**UNKNOWN**"));
                    container.addValue(" - First seen", getTime(data.getCreated(), current) + " ago");
                    container.addValue(" - Last seen", getTime(data.getUpdated(), current) + " ago on " + data.getServer());
                    container.addValue(" - IP Login Count", data.getCount());
                }
            }

            return container;
        }

        public AnalysisContainer getServerData(Collection<UUID> uuids, AnalysisContainer container) {
            return container;
        }

        private String getIp(Player player) {
            InetSocketAddress address = player.getAddress();
            if (address == null) {
                return null;
            }
            InetAddress host = address.getAddress();
            if (host == null) {
                return null;
            }
            return host.getHostAddress();
        }

        private String getName(UUID uuid) {
            try {
                return PlayerLookup.get(uuid).getName();
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
}
