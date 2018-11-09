package me.egg82.altfinder.services;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.core.SQLFetchResult;
import me.egg82.altfinder.utils.RedisUtil;
import ninja.egg82.json.JSONUtil;
import ninja.leaping.configurate.ConfigurationNode;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

public class Redis {
    private static final Logger logger = LoggerFactory.getLogger(Redis.class);

    private static final UUID serverId = UUID.randomUUID();
    public static UUID getServerID() { return serverId; }

    private Redis() {}

    public static CompletableFuture<Boolean> updateFromQueue(SQLFetchResult sqlResult, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis == null) {
                    return Boolean.FALSE;
                }

                for (PlayerData data : sqlResult.getData()) {
                    String ipKey = "altfndr:ip:" + data.getIP();
                    String uuidKey = "altfndr:uuid:" + data.getUUID();
                    String infoKey = "altfndr:info:" + data.getUUID() + "|" + data.getIP();

                    redis.sadd(ipKey, data.getUUID().toString());
                    redis.sadd(uuidKey, data.getIP());

                    JSONObject info = new JSONObject();
                    info.put("count", data.getCount());
                    info.put("server", data.getServer());
                    info.put("created", data.getCreated());
                    info.put("updated", data.getUpdated());

                    redis.set(infoKey, info.toJSONString());

                    info.put("ip", data.getIP());
                    info.put("uuid", data.getUUID().toString());
                    info.put("id", serverId.toString());

                    redis.publish("altfndr-info", info.toJSONString());
                }

                for (String key : sqlResult.getRemovedKeys()) {
                    redis.del(key);
                    if (key.indexOf('|') == -1) {
                        redis.publish("altfndr-delete", key.substring(key.lastIndexOf(':') + 1));
                    }
                }

                return Boolean.TRUE;
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> update(Set<PlayerData> sqlResults, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis == null) {
                    return Boolean.FALSE;
                }

                for (PlayerData data : sqlResults) {
                    String ipKey = "altfndr:ip:" + data.getIP();
                    String uuidKey = "altfndr:uuid:" + data.getUUID();
                    String infoKey = "altfndr:info:" + data.getUUID() + "|" + data.getIP();

                    redis.sadd(ipKey, data.getUUID().toString());
                    redis.sadd(uuidKey, data.getIP());

                    JSONObject info = new JSONObject();
                    info.put("count", data.getCount());
                    info.put("server", data.getServer());
                    info.put("created", data.getCreated());
                    info.put("updated", data.getUpdated());

                    redis.set(infoKey, info.toJSONString());

                    info.put("ip", data.getIP());
                    info.put("uuid", data.getUUID().toString());
                    info.put("id", serverId.toString());

                    redis.publish("altfndr-info", info.toJSONString());
                }

                return Boolean.TRUE;
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> update(PlayerData data, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis == null) {
                    return Boolean.FALSE;
                }

                String ipKey = "altfndr:ip:" + data.getIP();
                String uuidKey = "altfndr:uuid:" + data.getUUID();
                String infoKey = "altfndr:info:" + data.getUUID() + "|" + data.getIP();

                redis.sadd(ipKey, data.getUUID().toString());
                redis.sadd(uuidKey, data.getIP());

                JSONObject info = new JSONObject();
                info.put("count", data.getCount());
                info.put("server", data.getServer());
                info.put("created", data.getCreated());
                info.put("updated", data.getUpdated());

                redis.set(infoKey, info.toJSONString());

                info.put("ip", data.getIP());
                info.put("uuid", data.getUUID().toString());
                info.put("id", serverId.toString());

                redis.publish("altfndr-info", info.toJSONString());

                return Boolean.TRUE;
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> delete(String ip, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis == null) {
                    return Boolean.FALSE;
                }

                String ipKey = "altfndr:ip:" + ip;

                Set<String> data = redis.smembers(ipKey);
                if (data != null) {
                    for (String uuid : data) {
                        String uuidKey = "altfndr:uuid:" + uuid;
                        String infoKey = "altfndr:info:" + uuid + "|" + ip;
                        redis.del(infoKey);
                        redis.srem(uuidKey, ip);
                    }
                }
                redis.del(ipKey);

                redis.publish("altfndr-delete", ip);

                return Boolean.TRUE;
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> delete(UUID uuid, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis == null) {
                    return Boolean.FALSE;
                }

                String uuidKey = "altfndr:uuid:" + uuid;

                Set<String> data = redis.smembers(uuidKey);
                if (data != null) {
                    for (String ip : data) {
                        String ipKey = "altfndr:ip:" + ip;
                        String infoKey = "altfndr:info:" + uuid + "|" + ip;
                        redis.del(infoKey);
                        redis.srem(ipKey, uuid.toString());
                    }
                }
                redis.del(uuidKey);

                redis.publish("altfndr-delete", uuid.toString());

                return Boolean.TRUE;
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Set<PlayerData>> getResult(String ip, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            Set<PlayerData> result = new HashSet<>();

            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis != null) {
                    String ipKey = "altfndr:ip:" + ip;

                    // Grab IP info
                    Set<String> data = redis.smembers(ipKey);
                    if (data != null) {
                        for (String uuid : data) {
                            String infoKey = "altfndr:info:" + uuid + "|" + ip;

                            String infoString = redis.get(infoKey);
                            if (infoString == null) {
                                continue;
                            }

                            try {
                                JSONObject info = JSONUtil.parseObject(infoString);
                                long count = ((Number) info.get("count")).longValue();
                                String server = (String) info.get("server");
                                long created = ((Number) info.get("created")).longValue();
                                long updated = ((Number) info.get("updated")).longValue();

                                result.add(new PlayerData(UUID.fromString(uuid), ip, count, server, created, updated));
                            } catch (ParseException | ClassCastException ex) {
                                logger.error(ex.getMessage(), ex);
                            }
                        }
                    }
                }
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return result.isEmpty() ? null : result;
        });
    }

    public static CompletableFuture<Set<PlayerData>> getResult(UUID uuid, JedisPool pool, ConfigurationNode redisConfigNode) {
        return CompletableFuture.supplyAsync(() -> {
            Set<PlayerData> result = new HashSet<>();

            try (Jedis redis = RedisUtil.getRedis(pool, redisConfigNode)) {
                if (redis != null) {
                    String ipKey = "altfndr:uuid:" + uuid;

                    // Grab IP info
                    Set<String> data = redis.smembers(ipKey);
                    if (data != null) {
                        for (String ip : data) {
                            String infoKey = "altfndr:info:" + uuid + "|" + ip;

                            String infoString = redis.get(infoKey);
                            if (infoString == null) {
                                continue;
                            }

                            try {
                                JSONObject info = JSONUtil.parseObject(infoString);
                                long count = ((Number) info.get("count")).longValue();
                                String server = (String) info.get("server");
                                long created = ((Number) info.get("created")).longValue();
                                long updated = ((Number) info.get("updated")).longValue();

                                result.add(new PlayerData(uuid, ip, count, server, created, updated));
                            } catch (ParseException | ClassCastException ex) {
                                logger.error(ex.getMessage(), ex);
                            }
                        }
                    }
                }
            } catch (JedisException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return result.isEmpty() ? null : result;
        });
    }
}
