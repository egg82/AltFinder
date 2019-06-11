package me.egg82.altfinder.services;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.UUID;
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

    private static Charset utf8 = Charset.forName("UTF-8");

    private RabbitMQ() {}

    public static void broadcast(Set<PlayerData> sqlResult) {
        try (Channel channel = RabbitMQUtil.getChannel(RabbitMQUtil.getConnection())) {
            if (channel == null) {
                return;
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
                channel.basicPublish("altfndr-info", "", null, obj.toJSONString().getBytes(utf8));
            }
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public static void broadcast(PlayerData data) {
        try (Channel channel = RabbitMQUtil.getChannel(RabbitMQUtil.getConnection())) {
            if (channel == null) {
                return;
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
            channel.basicPublish("altfndr-info", "", null, obj.toJSONString().getBytes(utf8));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public static void delete(String ip) {
        try (Channel channel = RabbitMQUtil.getChannel(RabbitMQUtil.getConnection())) {
            if (channel == null) {
                return;
            }

            channel.exchangeDeclare("altfndr-delete", "fanout");
            channel.basicPublish("altfndr-delete", "", null, ip.getBytes(utf8));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }

    public static void delete(UUID uuid) {
        try (Channel channel = RabbitMQUtil.getChannel(RabbitMQUtil.getConnection())) {
            if (channel == null) {
                return;
            }

            channel.exchangeDeclare("altfndr-delete", "fanout");
            channel.basicPublish("altfndr-delete", "", null, uuid.toString().getBytes(utf8));
        } catch (IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }
    }
}
