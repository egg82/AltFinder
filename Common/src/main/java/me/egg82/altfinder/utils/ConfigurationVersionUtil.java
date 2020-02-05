package me.egg82.altfinder.utils;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationVersionUtil {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationVersionUtil.class);

    private ConfigurationVersionUtil() {}

    public static void conformVersion(ConfigurationLoader<ConfigurationNode> loader, ConfigurationNode config, File fileOnDisk) throws IOException {
        double oldVersion = config.getNode("version").getDouble(1.0d);

        if (config.getNode("version").getDouble(1.0d) == 1.0d) {
            to20(config);
        }
        if (config.getNode("version").getDouble() == 2.0d) {
            to30(config);
        }
        if (config.getNode("version").getDouble() == 3.0d) {
            to31(config);
        }
        if (config.getNode("version").getDouble() == 3.1d) {
            to42(config);
        }

        if (config.getNode("version").getDouble() != oldVersion) {
            File backupFile = new File(fileOnDisk.getParent(), fileOnDisk.getName() + ".bak");
            if (backupFile.exists()) {
                java.nio.file.Files.delete(backupFile.toPath());
            }

            Files.copy(fileOnDisk, backupFile);
            loader.save(config);
        }
    }

    private static void to20(ConfigurationNode config) {
        // Version
        config.getNode("version").setValue(2.0d);
    }

    private static void to30(ConfigurationNode config) {
        // sql -> storage
        String sqlType = config.getNode("sql", "type").getString("sqlite");
        int sqlThreads = config.getNode("sql", "threads").getInt(2);
        String sqlDatabase;
        if (sqlType.equalsIgnoreCase("sqlite")) {
            sqlDatabase = config.getNode("sql", "sqlite", "file").getString("altfinder");
            int dotIndex = sqlDatabase.indexOf('.');
            if (dotIndex > 0) {
                sqlDatabase = sqlDatabase.substring(0, dotIndex);
            }
        } else {
            sqlDatabase = config.getNode("sql", "mysql", "database").getString("altfinder");
        }
        String mysqlAddress = config.getNode("sql", "mysql", "address").getString("127.0.0.1");
        int mysqlPort = config.getNode("sql", "mysql", "port").getInt(3306);
        String mysqlUser = config.getNode("sql", "mysql", "user").getString("");
        String mysqlPass = config.getNode("sql", "mysql", "pass").getString("");
        config.removeChild("sql");
        config.getNode("storage", "method").setValue(sqlType);
        config.getNode("storage", "data", "address").setValue(mysqlAddress + ":" + mysqlPort);
        config.getNode("storage", "data", "database").setValue(sqlDatabase);
        config.getNode("storage", "data", "prefix").setValue("altfinder_");
        config.getNode("storage", "data", "username").setValue(mysqlUser);
        config.getNode("storage", "data", "password").setValue(mysqlPass);
        config.getNode("storage", "data", "mongodb", "collection-prefix").setValue("");
        config.getNode("storage", "data", "mongodb", "connection-uri").setValue("");
        config.getNode("storage", "settings", "max-pool-size").setValue(sqlThreads);
        config.getNode("storage", "settings", "min-idle").setValue(sqlThreads);
        config.getNode("storage", "settings", "max-lifetime").setValue(1800000L);
        config.getNode("storage", "settings", "timeout").setValue(5000L);
        config.getNode("storage", "settings", "properties", "unicode").setValue(Boolean.TRUE);
        config.getNode("storage", "settings", "properties", "encoding").setValue("utf8");

        // redis
        String redisAddress = config.getNode("redis", "address").getString("");
        if (redisAddress.isEmpty()) {
            redisAddress = "127.0.0.1";
        }
        int redisPort = config.getNode("redis", "port").getInt(6379);
        String redisPass = config.getNode("redis", "pass").getString("");
        config.getNode("redis").removeChild("port");
        config.getNode("redis").removeChild("pass");
        config.getNode("redis", "address").setValue(redisAddress + ":" + redisPort);
        config.getNode("redis", "password").setValue(redisPass);

        // rabbit -> rabbitmq
        boolean enabled = config.getNode("rabbit", "enabled").getBoolean(false);
        String rabbitAddress = config.getNode("rabbit", "address").getString("");
        if (rabbitAddress.isEmpty()) {
            rabbitAddress = "127.0.0.1";
        }
        int rabbitPort = config.getNode("rabbit", "port").getInt(5672);
        String rabbitUser = config.getNode("rabbit", "user").getString("guest");
        String rabbitPass = config.getNode("rabbit", "pass").getString("guest");
        config.removeChild("rabbit");
        config.getNode("rabbitmq", "enabled").setValue(enabled);
        config.getNode("rabbitmq", "address").setValue(rabbitAddress + ":" + rabbitPort);
        config.getNode("rabbitmq", "username").setValue(rabbitUser);
        config.getNode("rabbitmq", "password").setValue(rabbitPass);

        // Remove cacheTime
        config.removeChild("cacheTime");

        // Add debug
        config.getNode("debug").setValue(Boolean.FALSE);

        // Add stats
        config.getNode("stats", "usage").setValue(Boolean.TRUE);
        config.getNode("stats", "errors").setValue(Boolean.TRUE);

        // Add update
        config.getNode("update", "check").setValue(Boolean.TRUE);
        config.getNode("update", "notify").setValue(Boolean.TRUE);

        // Add ignore
        config.getNode("ignore").setValue(Arrays.asList("127.0.0.1", "localhost", "::1"));

        // Version
        config.getNode("version").setValue(3.0d);
    }

    private static void to31(ConfigurationNode config) {
        // Add storage->data->SSL
        config.getNode("storage", "data", "ssl").setValue(Boolean.FALSE);

        // Version
        config.getNode("version").setValue(3.1d);
    }

    private static void to42(ConfigurationNode config) {
        // Move storage
        String storageMethod = config.getNode("storage", "method").getString("sqlite");
        String storageAddress = config.getNode("storage", "data", "address").getString("127.0.0.1:3306");
        String storageDatabase = config.getNode("storage", "data", "database").getString("altfinder");
        String storagePrefix = config.getNode("storage", "data", "prefix").getString("altfinder_");
        String storageUser = config.getNode("storage", "data", "username").getString("");
        String storagePass = config.getNode("storage", "data", "password").getString("");
        boolean storageSSL = config.getNode("storage", "data", "ssl").getBoolean(false);
        int storageMaxPoolSize = config.getNode("storage", "settings", "max-pool-size").getInt(4);
        int storageMinIdle = config.getNode("storage", "settings", "min-idle").getInt(4);
        long storageMaxLifetime = config.getNode("storage", "settings", "max-lifetime").getLong(1800000L);
        long storageTimeout = config.getNode("storage", "settings", "timeout").getLong(5000L);
        boolean storageUnicode = config.getNode("storage", "settings", "unicode").getBoolean(true);
        String storageEncoding = config.getNode("storage", "settings", "encoding").getString("utf8");

        config.removeChild("storage");

        config.getNode("storage", "engines", "mysql", "enabled").setValue(storageMethod.equalsIgnoreCase("mysql"));
        config.getNode("storage", "engines", "mysql", "connection", "address").setValue(storageAddress);
        config.getNode("storage", "engines", "mysql", "connection", "database").setValue(storageDatabase);
        config.getNode("storage", "engines", "mysql", "connection", "prefix").setValue(storagePrefix);
        config.getNode("storage", "engines", "mysql", "connection", "username").setValue(storageUser);
        config.getNode("storage", "engines", "mysql", "connection", "password").setValue(storagePass);
        config.getNode("storage", "engines", "mysql", "connection", "options").setValue("useSSL=" + storageSSL + "&useUnicode=" + storageUnicode + "&characterEncoding=" + storageEncoding);
        config.getNode("storage", "engines", "redis", "enabled").setValue(Boolean.FALSE);
        config.getNode("storage", "engines", "redis", "connection", "address").setValue("127.0.0.1:6379");
        config.getNode("storage", "engines", "redis", "connection", "password").setValue("");
        config.getNode("storage", "engines", "redis", "connection", "prefix").setValue("altfinder:");
        config.getNode("storage", "engines", "sqlite", "enabled").setValue(storageMethod.equalsIgnoreCase("sqlite"));
        config.getNode("storage", "engines", "sqlite", "connection", "file").setValue(storageDatabase + ".db");
        config.getNode("storage", "engines", "sqlite", "connection", "prefix").setValue(storagePrefix);
        config.getNode("storage", "engines", "sqlite", "connection", "options").setValue("useUnicode=" + storageUnicode + "&characterEncoding=" + storageEncoding);
        config.getNode("storage", "settings", "max-pool-size").setValue(storageMaxPoolSize);
        config.getNode("storage", "settings", "min-idle").setValue(storageMinIdle);
        config.getNode("storage", "settings", "max-lifetime").setValue(storageMaxLifetime);
        config.getNode("storage", "settings", "timeout").setValue(storageTimeout);
        config.getNode("storage", "order").setValue(Arrays.asList("mysql", "redis", "sqlite"));

        // Move messaging
        boolean redisEnabled = config.getNode("redis", "enabled").getBoolean(false);
        String redisAddress = config.getNode("redis", "address").getString("127.0.0.1:6379");
        String redisPass = config.getNode("redis", "password").getString("");
        boolean rabbitEnabled = config.getNode("rabbitmq", "enabled").getBoolean(false);
        String rabbitAddress = config.getNode("rabbitmq", "address").getString("127.0.0.1:5672");
        String rabbitUser = config.getNode("rabbitmq", "username").getString("guest");
        String rabbitPass = config.getNode("rabbitmq", "password").getString("guest");
        int messagingMaxPoolSize = config.getNode("messaging", "settings", "max-pool-size").getInt(4);
        int messagingMinIdle = config.getNode("messaging", "settings", "min-idle").getInt(4);
        long messagingMaxLifetime = config.getNode("messaging", "settings", "max-lifetime").getLong(1800000L);
        long messagingTimeout = config.getNode("messaging", "settings", "timeout").getLong(5000L);

        config.removeChild("redis");
        config.removeChild("rabbitmq");

        config.getNode("messaging", "engines", "redis", "enabled").setValue(redisEnabled);
        config.getNode("messaging", "engines", "redis", "connection", "address").setValue(redisAddress);
        config.getNode("messaging", "engines", "redis", "connection", "password").setValue(redisPass);
        config.getNode("messaging", "engines", "rabbitmq", "enabled").setValue(rabbitEnabled);
        config.getNode("messaging", "engines", "rabbitmq", "connection", "address").setValue(rabbitAddress);
        config.getNode("messaging", "engines", "rabbitmq", "connection", "v-host").setValue("/");
        config.getNode("messaging", "engines", "rabbitmq", "connection", "username").setValue(rabbitUser);
        config.getNode("messaging", "engines", "rabbitmq", "connection", "password").setValue(rabbitPass);
        config.getNode("messaging", "settings", "max-pool-size").setValue(messagingMaxPoolSize);
        config.getNode("messaging", "settings", "min-idle").setValue(messagingMinIdle);
        config.getNode("messaging", "settings", "max-lifetime").setValue(messagingMaxLifetime);
        config.getNode("messaging", "settings", "timeout").setValue(messagingTimeout);
        config.getNode("messaging", "order").setValue(Arrays.asList("rabbitmq", "redis"));

        // Remove ignore
        config.removeChild("ignore");

        // Add lang
        config.getNode("lang").setValue("en");

        // Add action
        config.getNode("action", "total", "alt-max").setValue(-1);
        config.getNode("action", "total", "kick-message").setValue("&cPlease do not use more than {max} alt accounts!");
        config.getNode("action", "total", "commands").setValue(Collections.<String>emptyList());
        config.getNode("action", "current", "alt-max").setValue(-1);
        config.getNode("action", "current", "kick-message").setValue("&cPlease disconnect from one of your alts before re-joining!");
        config.getNode("action", "current", "commands").setValue(Collections.<String>emptyList());

        // Version
        config.getNode("version").setValue(4.2d);
    }
}
