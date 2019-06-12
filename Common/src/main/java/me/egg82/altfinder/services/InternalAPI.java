package me.egg82.altfinder.services;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import me.egg82.altfinder.APIException;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.enums.SQLType;
import me.egg82.altfinder.extended.CachedConfigValues;
import me.egg82.altfinder.sql.MySQL;
import me.egg82.altfinder.sql.SQLite;
import me.egg82.altfinder.utils.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalAPI {
    private static final Logger logger = LoggerFactory.getLogger(InternalAPI.class);

    public Set<PlayerData> getPlayerData(String ip) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Getting results for " + ip);
        }

        // Redis
        Optional<Set<PlayerData>> redisResult = Redis.getResult(ip);
        if (redisResult.isPresent()) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info(ip + " found in Redis.");
            }
            return redisResult.get();
        }

        // SQL
        try {
            Optional<Set<PlayerData>> result = Optional.empty();
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                result = MySQL.getData(ip);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                result = SQLite.getData(ip);
            }

            if (result.isPresent()) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.info(ip + " found in storage.");
                }
                // Update messaging/Redis, force same-thread
                Redis.update(result.get());
                RabbitMQ.broadcast(result.get());
                return result.get();
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        return new HashSet<>();
    }

    public Set<PlayerData> getPlayerData(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Getting results for " + uuid);
        }

        // Redis
        Optional<Set<PlayerData>> redisResult = Redis.getResult(uuid);
        if (redisResult.isPresent()) {
            if (ConfigUtil.getDebugOrFalse()) {
                logger.info(uuid + " found in Redis.");
            }
            return redisResult.get();
        }

        // SQL
        try {
            Optional<Set<PlayerData>> result = Optional.empty();
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                result = MySQL.getData(uuid);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                result = SQLite.getData(uuid);
            }

            if (result.isPresent()) {
                if (ConfigUtil.getDebugOrFalse()) {
                    logger.info(uuid + " found in storage.");
                }
                // Update messaging/Redis, force same-thread
                Redis.update(result.get());
                RabbitMQ.broadcast(result.get());
                return result.get();
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        return new HashSet<>();
    }

    public static void add(PlayerData data) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
            try {
                SQLite.add(data);
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
                throw new APIException(true, ex);
            }
        }
    }

    public static void delete(String search) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
            try {
                SQLite.delete(search);
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
                throw new APIException(true, ex);
            }
        }
    }

    public void add(UUID uuid, String ip, String server) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Setting new data for " + uuid + " (" + ip + ")");
        }

        // SQL
        PlayerData result = null;
        try {
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                result = MySQL.update(uuid, ip, server);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                result = SQLite.update(uuid, ip, server);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        if (result == null) {
            throw new APIException(true, "Could not add " + uuid + " (" + ip + ")");
        }

        // Redis
        Redis.update(result);

        // RabbitMQ
        RabbitMQ.broadcast(result);
    }

    public void remove(UUID uuid) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Removing data for " + uuid);
        }

        // SQL
        try {
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                MySQL.delete(uuid.toString());
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                SQLite.delete(uuid.toString());
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        // Redis
        Redis.delete(uuid);

        // RabbitMQ
        RabbitMQ.delete(uuid);
    }

    public void remove(String ip) throws APIException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get cached config.");
        }

        if (ConfigUtil.getDebugOrFalse()) {
            logger.info("Removing data for " + ip);
        }

        // SQL
        try {
            if (cachedConfig.get().getSQLType() == SQLType.MySQL) {
                MySQL.delete(ip);
            } else if (cachedConfig.get().getSQLType() == SQLType.SQLite) {
                SQLite.delete(ip);
            }
        } catch (SQLException ex) {
            logger.error(ex.getMessage(), ex);
            throw new APIException(true, ex);
        }

        // Redis
        Redis.delete(ip);

        // RabbitMQ
        RabbitMQ.delete(ip);
    }
}
