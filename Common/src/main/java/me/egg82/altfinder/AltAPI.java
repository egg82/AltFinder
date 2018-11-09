package me.egg82.altfinder;

import com.google.common.collect.ImmutableSet;
import com.rabbitmq.client.Connection;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.enums.SQLType;
import me.egg82.altfinder.extended.CachedConfigValues;
import me.egg82.altfinder.extended.Configuration;
import me.egg82.altfinder.services.InternalAPI;
import me.egg82.altfinder.sql.MySQL;
import me.egg82.altfinder.sql.SQLite;
import me.egg82.altfinder.utils.RabbitMQUtil;
import me.egg82.altfinder.utils.ValidationUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AltAPI {
    private static final Logger logger = LoggerFactory.getLogger(AltAPI.class);

    private static final AltAPI api = new AltAPI();
    private final InternalAPI internalApi = new InternalAPI();

    private AltAPI() {}

    public static AltAPI getInstance() { return api; }

    public long getCurrentSQLTime() {
        CachedConfigValues cachedConfig;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return -1L;
        }

        try {
            if (cachedConfig.getSQLType() == SQLType.MySQL) {
                return MySQL.getCurrentTime(cachedConfig.getSQL()).get();
            } else if (cachedConfig.getSQLType() == SQLType.SQLite) {
                return SQLite.getCurrentTime(cachedConfig.getSQL()).get();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        return -1L;
    }

    public void addPlayerData(UUID uuid, String ip, String server) {
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

        CachedConfigValues cachedConfig;
        Configuration config;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            config = ServiceLocator.get(Configuration.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        try (Connection rabbitConnection = RabbitMQUtil.getConnection(cachedConfig.getRabbitConnectionFactory())) {
            internalApi.add(uuid, ip, server, cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
            return;
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        internalApi.add(uuid, ip, server, cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
    }

    public void removePlayerData(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        CachedConfigValues cachedConfig;
        Configuration config;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            config = ServiceLocator.get(Configuration.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        try (Connection rabbitConnection = RabbitMQUtil.getConnection(cachedConfig.getRabbitConnectionFactory())) {
            internalApi.remove(uuid, cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
            return;
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        internalApi.remove(uuid, cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
    }

    public void removePlayerData(String ip) {
        if (ip == null) {
            throw new IllegalArgumentException("ip cannot be null.");
        }
        if (!ValidationUtil.isValidIp(ip)) {
            throw new IllegalArgumentException("ip is invalid.");
        }

        CachedConfigValues cachedConfig;
        Configuration config;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            config = ServiceLocator.get(Configuration.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        try (Connection rabbitConnection = RabbitMQUtil.getConnection(cachedConfig.getRabbitConnectionFactory())) {
            internalApi.remove(ip, cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
            return;
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        internalApi.remove(ip, cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug());
    }

    public ImmutableSet<PlayerData> getPlayerData(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        CachedConfigValues cachedConfig;
        Configuration config;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            config = ServiceLocator.get(Configuration.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return ImmutableSet.of();
        }

        try (Connection rabbitConnection = RabbitMQUtil.getConnection(cachedConfig.getRabbitConnectionFactory())) {
            return ImmutableSet.copyOf(internalApi.getPlayerData(uuid, cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug()));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return ImmutableSet.copyOf(internalApi.getPlayerData(uuid, cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug()));
    }

    public ImmutableSet<PlayerData> getPlayerData(String ip) {
        if (ip == null) {
            throw new IllegalArgumentException("ip cannot be null.");
        }
        if (!ValidationUtil.isValidIp(ip)) {
            throw new IllegalArgumentException("ip is invalid.");
        }

        CachedConfigValues cachedConfig;
        Configuration config;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            config = ServiceLocator.get(Configuration.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return ImmutableSet.of();
        }

        try (Connection rabbitConnection = RabbitMQUtil.getConnection(cachedConfig.getRabbitConnectionFactory())) {
            return ImmutableSet.copyOf(internalApi.getPlayerData(ip, cachedConfig.getRedisPool(), config.getNode("redis"), rabbitConnection, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug()));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return ImmutableSet.copyOf(internalApi.getPlayerData(ip, cachedConfig.getRedisPool(), config.getNode("redis"), null, cachedConfig.getSQL(), config.getNode("storage"), cachedConfig.getSQLType(), cachedConfig.getDebug()));
    }
}
