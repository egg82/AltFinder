package me.egg82.altfinder.utils;

import com.google.common.reflect.TypeToken;
import com.rabbitmq.client.ConnectionFactory;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;
import com.zaxxer.hikari.HikariConfig;
import java.io.*;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import me.egg82.altfinder.enums.SQLType;
import me.egg82.altfinder.extended.CachedConfigValues;
import me.egg82.altfinder.extended.Configuration;
import me.egg82.altfinder.extended.RabbitMQReceiver;
import net.kyori.text.TextComponent;
import net.kyori.text.format.TextColor;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.sql.SQL;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import redis.clients.jedis.JedisPool;

public class ConfigurationFileUtil {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationFileUtil.class);

    private ConfigurationFileUtil() {}

    public static void reloadConfig(Object plugin, ProxyServer proxy, PluginDescription description) {
        Configuration config;
        try {
            config = getConfig(plugin, "config.yml", new File(new File(description.getSource().get().getParent().toFile(), description.getName().get()), "config.yml"));
        } catch (IOException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        }

        boolean debug = config.getNode("debug").getBoolean(false);

        if (debug) {
            proxy.getConsoleCommandSource().sendMessage(LogUtil.getHeading().append(TextComponent.of("Debug ").color(TextColor.YELLOW)).append(TextComponent.of("enabled").color(TextColor.WHITE)).build());
        }

        Set<String> ignored;
        try {
            ignored = new HashSet<>(config.getNode("ignore").getList(TypeToken.of(String.class)));
        } catch (ObjectMappingException ex) {
            logger.error(ex.getMessage(), ex);
            ignored = new HashSet<>();
        }

        if (debug) {
            for (String value : ignored) {
                proxy.getConsoleCommandSource().sendMessage(LogUtil.getHeading().append(TextComponent.of("Ignoring: ").color(TextColor.YELLOW)).append(TextComponent.of(value).color(TextColor.WHITE)).build());
            }
        }

        try {
            destroyServices(ServiceLocator.getOptional(CachedConfigValues.class), ServiceLocator.getOptional(RabbitMQReceiver.class));
        } catch (InstantiationException | IllegalAccessException | IOException | TimeoutException ex) {
            logger.error(ex.getMessage(), ex);
        }

        CachedConfigValues cachedValues = CachedConfigValues.builder()
                .debug(debug)
                .ignored(ignored)
                .redisPool(getRedisPool(proxy, config.getNode("redis")))
                .rabbitConnectionFactory(getRabbitConnectionFactory(proxy, config.getNode("rabbitmq")))
                .sql(getSQL(proxy, description, config.getNode("storage")))
                .sqlType(config.getNode("storage", "method").getString("sqlite"))
                .serverName(ServerNameUtil.getName(new File(new File(description.getSource().get().getParent().toFile(), description.getName().get()), "server-name.txt")))
                .build();

        ServiceLocator.register(config);
        ServiceLocator.register(cachedValues);

        if (debug) {
            proxy.getConsoleCommandSource().sendMessage(LogUtil.getHeading().append(TextComponent.of("Using Redis: ").color(TextColor.YELLOW)).append(TextComponent.of(String.valueOf(cachedValues.getRedisPool() != null)).color(TextColor.WHITE)).build());
            proxy.getConsoleCommandSource().sendMessage(LogUtil.getHeading().append(TextComponent.of("Using RabbitMQ: ").color(TextColor.YELLOW)).append(TextComponent.of(String.valueOf(cachedValues.getRabbitConnectionFactory() != null)).color(TextColor.WHITE)).build());
            proxy.getConsoleCommandSource().sendMessage(LogUtil.getHeading().append(TextComponent.of("SQL type: ").color(TextColor.YELLOW)).append(TextComponent.of(cachedValues.getSQLType().name()).color(TextColor.WHITE)).build());
        }
    }

    public static Configuration getConfig(Object plugin, String resourcePath, File fileOnDisk) throws IOException {
        File parentDir = fileOnDisk.getParentFile();
        if (parentDir.exists() && !parentDir.isDirectory()) {
            Files.delete(parentDir.toPath());
        }
        if (!parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Could not create parent directory structure.");
            }
        }
        if (fileOnDisk.exists() && fileOnDisk.isDirectory()) {
            Files.delete(fileOnDisk.toPath());
        }

        if (!fileOnDisk.exists()) {
            try (InputStreamReader reader = new InputStreamReader(plugin.getClass().getClassLoader().getResourceAsStream(resourcePath));
                 BufferedReader in = new BufferedReader(reader);
                 FileWriter writer = new FileWriter(fileOnDisk);
                 BufferedWriter out = new BufferedWriter(writer)) {
                String line;
                while ((line = in.readLine()) != null) {
                    out.write(line + System.lineSeparator());
                }
            }
        }

        ConfigurationLoader<ConfigurationNode> loader = YAMLConfigurationLoader.builder().setFlowStyle(DumperOptions.FlowStyle.BLOCK).setIndent(2).setFile(fileOnDisk).build();
        ConfigurationNode root = loader.load(ConfigurationOptions.defaults().setHeader("Comments are gone because update :(. Click here for new config + comments: https://www.spigotmc.org/resources/altfinder.57678/"));
        Configuration config = new Configuration(root);
        ConfigurationVersionUtil.conformVersion(loader, config, fileOnDisk);

        return config;
    }

    private static void destroyServices(Optional<CachedConfigValues> cachedConfigValues, Optional<RabbitMQReceiver> rabbitReceiver) throws IOException, TimeoutException {
        if (!cachedConfigValues.isPresent()) {
            return;
        }

        cachedConfigValues.get().getSQL().close();

        if (cachedConfigValues.get().getRedisPool() != null) {
            cachedConfigValues.get().getRedisPool().close();
        }

        if (rabbitReceiver.isPresent()) {
            rabbitReceiver.get().close();
        }
    }

    private static SQL getSQL(ProxyServer proxy, PluginDescription description, ConfigurationNode storageConfigNode) {
        SQLType type = SQLType.getByName(storageConfigNode.getNode("method").getString("sqlite"));
        if (type == SQLType.UNKNOWN) {
            proxy.getConsoleCommandSource().sendMessage(TextComponent.of(TextColor.YELLOW + "storage.method is an unknown value. Using default value."));
            type = SQLType.SQLite;
        }

        HikariConfig hikariConfig = new HikariConfig();
        if (type == SQLType.MySQL) {
            hikariConfig.setJdbcUrl("jdbc:mysql://" + storageConfigNode.getNode("data", "address").getString("127.0.0.1:3306") + "/" + storageConfigNode.getNode("data", "database").getString("altfinder"));
            hikariConfig.setConnectionTestQuery("SELECT 1;");
        } else if (type == SQLType.SQLite) {
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + new File(new File(description.getSource().get().getParent().toFile(), description.getName().get()), storageConfigNode.getNode("data", "database").getString("altfinder") + ".db").getAbsolutePath());
            hikariConfig.setConnectionTestQuery("SELECT 1;");
        }
        hikariConfig.setUsername(storageConfigNode.getNode("data", "username").getString(""));
        hikariConfig.setPassword(storageConfigNode.getNode("data", "password").getString(""));
        hikariConfig.setMaximumPoolSize(storageConfigNode.getNode("settings", "max-pool-size").getInt(2));
        hikariConfig.setMinimumIdle(storageConfigNode.getNode("settings", "min-idle").getInt(2));
        hikariConfig.setMaxLifetime(storageConfigNode.getNode("settings", "max-lifetime").getLong(1800000L));
        hikariConfig.setConnectionTimeout(storageConfigNode.getNode("settings", "timeout").getLong(5000L));
        hikariConfig.addDataSourceProperty("useUnicode", String.valueOf(storageConfigNode.getNode("settings", "properties", "unicode").getBoolean(true)));
        hikariConfig.addDataSourceProperty("characterEncoding", storageConfigNode.getNode("settings", "properties", "encoding").getString("utf8"));
        hikariConfig.addDataSourceProperty("useLegacyDatetimeCode", false);
        hikariConfig.addDataSourceProperty("serverTimezone", "UTC");
        hikariConfig.setAutoCommit(true);

        // Optimizations
        if (type == SQLType.MySQL) {
            hikariConfig.addDataSourceProperty("useSSL", String.valueOf(storageConfigNode.getNode("data", "ssl").getBoolean(false)));
            // http://assets.en.oreilly.com/1/event/21/Connector_J%20Performance%20Gems%20Presentation.pdf
            hikariConfig.addDataSourceProperty("cacheServerConfiguration", "true");
            hikariConfig.addDataSourceProperty("useLocalSessionState", "true");
            hikariConfig.addDataSourceProperty("useLocalTransactionState", "true");
            hikariConfig.addDataSourceProperty("rewriteBatchedStatements", "true");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("maintainTimeStats", "false");
            hikariConfig.addDataSourceProperty("useUnbufferedIO", "false");
            hikariConfig.addDataSourceProperty("useReadAheadInput", "false");
            // https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("cacheResultSetMetadata", "true");
            hikariConfig.addDataSourceProperty("elideSetAutoCommits", "true");
        }

        return new SQL(hikariConfig);
    }

    private static JedisPool getRedisPool(ProxyServer proxy, ConfigurationNode redisConfigNode) {
        if (!redisConfigNode.getNode("enabled").getBoolean(false)) {
            return null;
        }

        String address = redisConfigNode.getNode("address").getString("127.0.0.1:6379");
        int portIndex = address.indexOf(':');
        int port;
        if (portIndex > -1) {
            port = Integer.parseInt(address.substring(portIndex + 1));
            address = address.substring(0, portIndex);
        } else {
            proxy.getConsoleCommandSource().sendMessage(TextComponent.of(TextColor.YELLOW + "redis.address port is an unknown value. Using default value."));
            port = 6379;
        }

        return new JedisPool(address, port);
    }

    private static ConnectionFactory getRabbitConnectionFactory(ProxyServer proxy, ConfigurationNode rabbitConfigNode) {
        if (!rabbitConfigNode.getNode("enabled").getBoolean(false)) {
            return null;
        }

        String address = rabbitConfigNode.getNode("address").getString("127.0.0.1:5672");
        int portIndex = address.indexOf(':');
        int port;
        if (portIndex > -1) {
            port = Integer.parseInt(address.substring(portIndex + 1));
            address = address.substring(0, portIndex);
        } else {
            proxy.getConsoleCommandSource().sendMessage(TextComponent.of(TextColor.YELLOW + "rabbitmq.address port is an unknown value. Using default value."));
            port = 5672;
        }

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(address);
        factory.setPort(port);
        factory.setVirtualHost("/");
        factory.setUsername(rabbitConfigNode.getNode("username").getString("guest"));
        factory.setPassword(rabbitConfigNode.getNode("password").getString("guest"));

        return factory;
    }
}
