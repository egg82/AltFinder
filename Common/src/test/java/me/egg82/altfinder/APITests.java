package me.egg82.altfinder;

import com.google.common.reflect.TypeToken;
import com.rabbitmq.client.ConnectionFactory;
import com.zaxxer.hikari.HikariConfig;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import me.egg82.altfinder.enums.SQLType;
import me.egg82.altfinder.extended.CachedConfigValues;
import me.egg82.altfinder.extended.Configuration;
import me.egg82.altfinder.utils.ConfigUtil;
import me.egg82.altfinder.utils.ServiceUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.sql.SQL;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.DumperOptions;
import redis.clients.jedis.JedisPool;

public class APITests {
    @Test
    public void testCurrentSQLTime() {
        Assertions.assertDoesNotThrow(() -> {
            preflight();
            System.out.println(AltAPI.getInstance().getCurrentSQLTime());
        });
    }

    private void preflight() throws IOException, APIException, ObjectMappingException, URISyntaxException {
        if (!ServiceLocator.isInitialized(Configuration.class)) {
            ConfigurationLoader<ConfigurationNode> loader = YAMLConfigurationLoader.builder().setFlowStyle(DumperOptions.FlowStyle.BLOCK).setIndent(2).setURL(getClass().getResource("config-test.yml")).build();
            ConfigurationNode root = loader.load(ConfigurationOptions.defaults().setHeader("Comments are gone because update :(. Click here for new config + comments: https://www.spigotmc.org/resources/altfinder.57678/"));
            ServiceLocator.register(new Configuration(root));
        }
        if (!ServiceLocator.isInitialized(CachedConfigValues.class)) {
            Optional<Configuration> config = ConfigUtil.getConfig();
            if (!config.isPresent()) {
                throw new APIException(true, "Could not get config.");
            }

            boolean debug = config.get().getNode("debug").getBoolean(false);

            Set<String> ignored = new HashSet<>(config.get().getNode("ignore").getList(TypeToken.of(String.class)));

            ServiceLocator.register(CachedConfigValues.builder()
                    .debug(debug)
                    .ignored(ignored)
                    .redisPool(getRedisPool(config.get().getNode("redis")))
                    .rabbitConnectionFactory(getRabbitConnectionFactory(config.get().getNode("rabbitmq")))
                    .sql(getSQL(config.get().getNode("storage")))
                    .sqlType(config.get().getNode("storage", "method").getString("sqlite"))
                    .serverName("test")
                    .build()
            );

            ServiceUtil.registerWorkPool();
            ServiceUtil.registerSQL();
        }
    }

    private SQL getSQL(ConfigurationNode storageConfigNode) throws URISyntaxException {
        SQLType type = SQLType.getByName(storageConfigNode.getNode("method").getString("sqlite"));
        if (type == SQLType.UNKNOWN) {
            type = SQLType.SQLite;
        }

        HikariConfig hikariConfig = new HikariConfig();
        if (type == SQLType.MySQL) {
            hikariConfig.setJdbcUrl("jdbc:mysql://" + storageConfigNode.getNode("data", "address").getString("127.0.0.1:3306") + "/" + storageConfigNode.getNode("data", "database").getString("altfinder"));
            hikariConfig.setConnectionTestQuery("SELECT 1;");
        } else if (type == SQLType.SQLite) {
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + new File(getCurrentDirectory(), storageConfigNode.getNode("data", "database").getString("altfinder") + ".db").getAbsolutePath());
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

    private static JedisPool getRedisPool(ConfigurationNode redisConfigNode) {
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
            port = 6379;
        }

        return new JedisPool(address, port);
    }

    private static ConnectionFactory getRabbitConnectionFactory(ConfigurationNode rabbitConfigNode) {
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

    private File getCurrentDirectory() throws URISyntaxException {
        return new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
    }
}
