package me.egg82.altfinder.services;

import com.rabbitmq.client.Connection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.enums.SQLType;
import me.egg82.altfinder.sql.MySQL;
import me.egg82.altfinder.sql.SQLite;
import ninja.egg82.sql.SQL;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

public class InternalAPI {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public Set<PlayerData> getPlayerData(String ip, JedisPool redisPool, ConfigurationNode redisConfigNode, Connection rabbitConnection, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType, boolean debug) {
        if (debug) {
            logger.info("Getting results for " + ip);
        }

        // Redis
        try {
            Set<PlayerData> result = Redis.getResult(ip, redisPool, redisConfigNode).get();
            if (result != null) {
                if (debug) {
                    logger.info(ip + " found in Redis.");
                }
                return result;
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        // SQL
        try {
            Set<PlayerData> result = null;
            if (sqlType == SQLType.MySQL) {
                result = MySQL.getData(ip, sql, storageConfigNode).get();
            } else if (sqlType == SQLType.SQLite) {
                result = SQLite.getData(ip, sql, storageConfigNode).get();
            }

            if (result != null) {
                if (debug) {
                    logger.info(ip + " found in storage.");
                }
                // Update messaging/Redis, force same-thread
                Redis.update(result, redisPool, redisConfigNode).get();
                RabbitMQ.broadcast(result, rabbitConnection).get();
                return result;
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        return new HashSet<>();
    }

    public Set<PlayerData> getPlayerData(UUID uuid, JedisPool redisPool, ConfigurationNode redisConfigNode, Connection rabbitConnection, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType, boolean debug) {
        if (debug) {
            logger.info("Getting results for " + uuid);
        }

        // Redis
        try {
            Set<PlayerData> result = Redis.getResult(uuid, redisPool, redisConfigNode).get();
            if (result != null ) {
                if (debug) {
                    logger.info(uuid + " found in Redis.");
                }
                return result;
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        // SQL
        try {
            Set<PlayerData> result = null;
            if (sqlType == SQLType.MySQL) {
                result = MySQL.getData(uuid, sql, storageConfigNode).get();
            } else if (sqlType == SQLType.SQLite) {
                result = SQLite.getData(uuid, sql, storageConfigNode).get();
            }

            if (result != null) {
                if (debug) {
                    logger.info(uuid + " found in storage.");
                }
                // Update messaging/Redis, force same-thread
                Redis.update(result, redisPool, redisConfigNode).get();
                RabbitMQ.broadcast(result, rabbitConnection).get();
                return result;
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        return new HashSet<>();
    }

    public static void add(PlayerData data, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType) {
        if (sqlType == SQLType.SQLite) {
            SQLite.add(data, sql, storageConfigNode);
        }
    }

    public void add(UUID uuid, String ip, String server, JedisPool redisPool, ConfigurationNode redisConfigNode, Connection rabbitConnection, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType, boolean debug) {
        if (debug) {
            logger.info("Setting new data for " + uuid + " (" + ip + ")");
        }

        // SQL
        PlayerData result = null;
        try {
            if (sqlType == SQLType.MySQL) {
                result = MySQL.update(sql, storageConfigNode, uuid, ip, server).get();
            } else if (sqlType == SQLType.SQLite) {
                result = SQLite.update(sql, storageConfigNode, uuid, ip, server).get();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        if (result == null) {
            return;
        }

        // Redis
        Redis.update(result, redisPool, redisConfigNode);

        // RabbitMQ
        RabbitMQ.broadcast(result, rabbitConnection);
    }

    public void remove(UUID uuid, JedisPool redisPool, ConfigurationNode redisConfigNode, Connection rabbitConnection, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType, boolean debug) {
        if (debug) {
            logger.info("Removing data for " + uuid);
        }

        // SQL
        if (sqlType == SQLType.MySQL) {
            MySQL.delete(uuid.toString(), sql, storageConfigNode);
        } else if (sqlType == SQLType.SQLite) {
            SQLite.delete(uuid.toString(), sql, storageConfigNode);
        }

        // Redis
        Redis.delete(uuid, redisPool, redisConfigNode);

        // RabbitMQ
        RabbitMQ.delete(uuid, rabbitConnection);
    }

    public void remove(String ip, JedisPool redisPool, ConfigurationNode redisConfigNode, Connection rabbitConnection, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType, boolean debug) {
        if (debug) {
            logger.info("Removing data for " + ip);
        }

        // SQL
        if (sqlType == SQLType.MySQL) {
            MySQL.delete(ip, sql, storageConfigNode);
        } else if (sqlType == SQLType.SQLite) {
            SQLite.delete(ip, sql, storageConfigNode);
        }

        // Redis
        Redis.delete(ip, redisPool, redisConfigNode);

        // RabbitMQ
        RabbitMQ.delete(ip, rabbitConnection);
    }

    public static void delete(String search, SQL sql, ConfigurationNode storageConfigNode, SQLType sqlType) {
        if (sqlType == SQLType.SQLite) {
            SQLite.delete(search, sql, storageConfigNode);
        }
    }
}
