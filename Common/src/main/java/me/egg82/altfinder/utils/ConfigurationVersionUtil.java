package me.egg82.altfinder.utils;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;

public class ConfigurationVersionUtil {
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
}
