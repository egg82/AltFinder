package me.egg82.altfinder.services.lookup;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import ninja.egg82.json.JSONUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VelocityPlayerInfo implements PlayerInfo {
    private static final Logger logger = LoggerFactory.getLogger(VelocityPlayerInfo.class);

    private UUID uuid;
    private String name;

    private static final Cache<UUID, String> uuidCache = Caffeine.newBuilder().expireAfterWrite(1L, TimeUnit.HOURS).build();
    private static final Cache<String, UUID> nameCache = Caffeine.newBuilder().expireAfterWrite(1L, TimeUnit.HOURS).build();

    private static final Object uuidCacheLock = new Object();
    private static final Object nameCacheLock = new Object();

    public VelocityPlayerInfo(UUID uuid, ProxyServer proxy) throws IOException {
        this.uuid = uuid;

        Optional<String> name = Optional.ofNullable(uuidCache.getIfPresent(uuid));
        if (!name.isPresent()) {
            synchronized (uuidCacheLock) {
                name = Optional.ofNullable(uuidCache.getIfPresent(uuid));
                if (!name.isPresent()) {
                    name = Optional.ofNullable(getNameExpensive(uuid, proxy));
                    uuidCache.put(uuid, name.isPresent() ? name.get() : null);
                }
            }
        }

        this.name = name.isPresent() ? name.get() : null;
    }

    public VelocityPlayerInfo(String name, ProxyServer proxy) throws IOException {
        this.name = name;

        Optional<UUID> uuid = Optional.ofNullable(nameCache.getIfPresent(name));
        if (!uuid.isPresent()) {
            synchronized (nameCacheLock) {
                uuid = Optional.ofNullable(nameCache.getIfPresent(name));
                if (!uuid.isPresent()) {
                    uuid = Optional.ofNullable(getUUIDExpensive(name, proxy));
                    nameCache.put(name, uuid.isPresent() ? uuid.get() : null);
                }
            }
        }

        this.uuid = uuid.isPresent() ? uuid.get() : null;
    }

    public UUID getUUID() { return uuid; }

    public String getName() { return name; }

    private static String getNameExpensive(UUID uuid, ProxyServer proxy) throws IOException {
        // Currently-online lookup
        Optional<Player> player = proxy.getPlayer(uuid);
        if (player.isPresent()) {
            return player.get().getUsername();
        }

        // Network lookup
        HttpURLConnection conn = getConnection("https://api.mojang.com/user/profiles/" + uuid.toString().replace("-", "") + "/names");

        int code = conn.getResponseCode();
        try (InputStream in = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
             InputStreamReader reader = new InputStreamReader(in);
             BufferedReader buffer = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = buffer.readLine()) != null) {
                builder.append(line);
            }

            if (code == 200) {
                JSONArray json = JSONUtil.parseArray(builder.toString());
                JSONObject last = (JSONObject) json.get(json.size() - 1);
                String name = (String) last.get("name");

                nameCache.put(name, uuid);
                return name;
            } else if (code == 204) {
                // No data exists
                return null;
            }
        } catch (ParseException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return null;
    }

    private static UUID getUUIDExpensive(String name, ProxyServer proxy) throws IOException {
        // Currently-online lookup
        Optional<Player> player = proxy.getPlayer(name);
        if (player.isPresent()) {
            return player.get().getUniqueId();
        }

        // Network lookup
        HttpURLConnection conn = getConnection("https://api.mojang.com/users/profiles/minecraft/" + name);

        int code = conn.getResponseCode();
        try (InputStream in = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
             InputStreamReader reader = new InputStreamReader(in);
             BufferedReader buffer = new BufferedReader(reader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = buffer.readLine()) != null) {
                builder.append(line);
            }

            if (code == 200) {
                JSONObject json = JSONUtil.parseObject(builder.toString());
                UUID uuid = UUID.fromString(((String) json.get("id")).replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"));
                name = (String) json.get("name");

                uuidCache.put(uuid, name);
                return uuid;
            } else if (code == 204) {
                // No data exists
                return null;
            }
        } catch (ParseException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return null;
    }

    private static HttpURLConnection getConnection(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();

        conn.setDoInput(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Connection", "close");
        conn.setRequestProperty("User-Agent", "egg82/VelocityPlayerInfo");
        conn.setRequestMethod("GET");

        return conn;
    }
}
