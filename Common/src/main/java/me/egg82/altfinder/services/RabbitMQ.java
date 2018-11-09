package me.egg82.altfinder.services;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.utils.RabbitMQUtil;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQ {
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQ.class);

    private static final UUID serverId = UUID.randomUUID();
    public static UUID getServerID() { return serverId; }

    private RabbitMQ() {}

    public static CompletableFuture<Boolean> broadcast(Set<PlayerData> sqlResult, Connection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try (Channel channel = RabbitMQUtil.getChannel(connection)) {
                if (channel == null) {
                    return Boolean.FALSE;
                }

                for (PlayerData data : sqlResult) {
                    JSONObject obj = new JSONObject();
                    obj.put("uuid", data.getUUID().toString());
                    obj.put("ip", data.getIP());
                    obj.put("count", data.getCount());
                    obj.put("server", data.getServer());
                    obj.put("created", data.getCreated());
                    obj.put("updated", data.getUpdated());
                    obj.put("id", serverId.toString());

                    channel.exchangeDeclare("altfndr-info", "fanout");
                    channel.basicPublish("altfndr-info", "", null, obj.toJSONString().getBytes());
                }

                return Boolean.TRUE;
            } catch (IOException | TimeoutException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> broadcast(PlayerData data, Connection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try (Channel channel = RabbitMQUtil.getChannel(connection)) {
                if (channel == null) {
                    return Boolean.FALSE;
                }

                JSONObject obj = new JSONObject();
                obj.put("uuid", data.getUUID().toString());
                obj.put("ip", data.getIP());
                obj.put("count", data.getCount());
                obj.put("server", data.getServer());
                obj.put("created", data.getCreated());
                obj.put("updated", data.getUpdated());
                obj.put("id", serverId.toString());

                channel.exchangeDeclare("altfndr-info", "fanout");
                channel.basicPublish("altfndr-info", "", null, obj.toJSONString().getBytes());

                return Boolean.TRUE;
            } catch (IOException | TimeoutException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> delete(String ip, Connection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try (Channel channel = RabbitMQUtil.getChannel(connection)) {
                if (channel == null) {
                    return Boolean.FALSE;
                }

                channel.exchangeDeclare("altfndr-delete", "fanout");
                channel.basicPublish("altfndr-delete", "", null, ip.getBytes());

                return Boolean.TRUE;
            } catch (IOException | TimeoutException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }

    public static CompletableFuture<Boolean> delete(UUID uuid, Connection connection) {
        return CompletableFuture.supplyAsync(() -> {
            try (Channel channel = RabbitMQUtil.getChannel(connection)) {
                if (channel == null) {
                    return Boolean.FALSE;
                }

                channel.exchangeDeclare("altfndr-delete", "fanout");
                channel.basicPublish("altfndr-delete", "", null, uuid.toString().getBytes());

                return Boolean.TRUE;
            } catch (IOException | TimeoutException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return Boolean.FALSE;
        });
    }
}
