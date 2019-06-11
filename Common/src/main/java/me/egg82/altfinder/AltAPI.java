package me.egg82.altfinder;

import com.google.common.collect.ImmutableSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.enums.SQLType;
import me.egg82.altfinder.extended.CachedConfigValues;
import me.egg82.altfinder.services.InternalAPI;
import me.egg82.altfinder.sql.MySQL;
import me.egg82.altfinder.sql.SQLite;
import me.egg82.altfinder.utils.ConfigUtil;
import me.egg82.altfinder.utils.ValidationUtil;

public class AltAPI {
    private static final AltAPI api = new AltAPI();
    private final InternalAPI internalApi = new InternalAPI();

    private AltAPI() {}

    public static AltAPI getInstance() { return api; }

    public long getCurrentSQLTime() throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        try {
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                return MySQL.getCurrentTime();
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                return SQLite.getCurrentTime();
            }
        } catch (SQLException ex) {
            throw new APIException(true, ex);
        }

        throw new APIException(true, "Could not get time from database.");
    }

    public void addPlayerData(UUID uuid, String ip, String server) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (ip == null) {
            throw new IllegalArgumentException("ip cannot be null.");
        }
        if (!ValidationUtil.isValidIp(ip)) {
            throw new IllegalArgumentException("ip is invalid.");
        }
        if (server == null) {
            throw new IllegalArgumentException("server cannot be null.");
        }

        internalApi.add(uuid, ip, server);
    }

    public void removePlayerData(UUID uuid) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        internalApi.remove(uuid);
    }

    public void removePlayerData(String ip) throws APIException {
        if (ip == null) {
            throw new IllegalArgumentException("ip cannot be null.");
        }
        if (!ValidationUtil.isValidIp(ip)) {
            throw new IllegalArgumentException("ip is invalid.");
        }

        internalApi.remove(ip);
    }

    public ImmutableSet<PlayerData> getPlayerData(UUID uuid) throws APIException {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        return ImmutableSet.copyOf(internalApi.getPlayerData(uuid));
    }

    public ImmutableSet<PlayerData> getPlayerData(String ip) throws APIException {
        if (ip == null) {
            throw new IllegalArgumentException("ip cannot be null.");
        }
        if (!ValidationUtil.isValidIp(ip)) {
            throw new IllegalArgumentException("ip is invalid.");
        }

        return ImmutableSet.copyOf(internalApi.getPlayerData(ip));
    }
}
