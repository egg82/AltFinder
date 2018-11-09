package me.egg82.altfinder.sql;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.core.SQLFetchResult;
import me.egg82.altfinder.utils.ValidationUtil;
import ninja.egg82.core.SQLQueryResult;
import ninja.egg82.sql.SQL;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MySQL {
    private static final Logger logger = LoggerFactory.getLogger(MySQL.class);

    private MySQL() {}

    public static CompletableFuture<Void> createTables(SQL sql, ConfigurationNode storageConfigNode) {
        String databaseName = storageConfigNode.getNode("data", "database").getString();
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "altfinder_";

        return CompletableFuture.runAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='" + tablePrefix.substring(0, tablePrefix.length() - 1) + "';", databaseName);
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` ("
                        + "`ip` VARCHAR(45) NOT NULL,"
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`count` UNSIGNED BIGINT NOT NULL,"
                        + "`server` VARCHAR(255) NOT NULL,"
                        + "`created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ");");
                sql.execute("ALTER TABLE `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` ADD UNIQUE (`ip`, `uuid`);");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }

            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name='" + tablePrefix + "queue';", databaseName);
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "queue` ("
                        + "`ip` VARCHAR(45) NOT NULL,"
                        + "`uuid` VARCHAR(36) NOT NULL,"
                        + "`count` UNSIGNED BIGINT NOT NULL,"
                        + "`server` VARCHAR(255) NOT NULL,"
                        + "`created` TIMESTAMP NOT NULL,"
                        + "`updated` TIMESTAMP NOT NULL"
                        + ");");
                sql.execute("ALTER TABLE `" + tablePrefix + "queue` ADD UNIQUE (`ip`, `uuid`);");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<SQLFetchResult> loadInfo(SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "altfinder_";

        return CompletableFuture.supplyAsync(() -> {
            List<PlayerData> data = new ArrayList<>();
            List<String> removedKeys = new ArrayList<>();

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `ip`, `count`, `server`, `created`, `updated` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "`;");

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Validate UUID/IP and remove bad data
                    if (!ValidationUtil.isValidUuid((String) o[0])) {
                        removedKeys.add("altfndr:uuid:" + o[0]);
                        removedKeys.add("altfndr:info:" + o[0] + "|" + o[1]);
                        sql.execute("DELETE FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `uuid`=?;", o[0]);
                        continue;
                    }
                    if (!ValidationUtil.isValidIp((String) o[1])) {
                        removedKeys.add("altfndr:ip:" + o[1]);
                        removedKeys.add("altfndr:info:" + o[0] + "|" + o[1]);
                        sql.execute("DELETE FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `ip`=?;", o[1]);
                        continue;
                    }

                    // Grab all data and convert to more useful object types
                    UUID uuid = UUID.fromString((String) o[0]);
                    String ip = (String) o[1];
                    long count = ((Number) o[2]).longValue();
                    String server = (String) o[3];
                    long created = ((Timestamp) o[4]).getTime();
                    long updated = ((Timestamp) o[5]).getTime();

                    data.add(new PlayerData(uuid, ip, count, server, created, updated));
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return new SQLFetchResult(data.toArray(new PlayerData[0]), removedKeys.toArray(new String[0]));
        });
    }

    public static CompletableFuture<SQLFetchResult> fetchQueue(SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "altfinder_";

        return CompletableFuture.supplyAsync(() -> {
            List<PlayerData> data = new ArrayList<>();
            List<String> removedKeys = new ArrayList<>();

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `ip`, `count`, `server`, `created`, `updated` FROM `" + tablePrefix + "queue` ORDER BY `updated` ASC;");

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Validate UUID/IP and remove bad data
                    if (!ValidationUtil.isValidUuid((String) o[0])) {
                        removedKeys.add("altfndr:uuid:" + o[0]);
                        removedKeys.add("altfndr:info:" + o[0] + "|" + o[1]);
                        sql.execute("DELETE FROM `" + tablePrefix + "queue` WHERE `uuid`=?;", o[0]);
                        continue;
                    }
                    if (!ValidationUtil.isValidIp((String) o[1])) {
                        removedKeys.add("altfndr:ip:" + o[1]);
                        removedKeys.add("altfndr:info:" + o[0] + "|" + o[1]);
                        sql.execute("DELETE FROM `" + tablePrefix + "queue` WHERE `ip`=?;", o[1]);
                        continue;
                    }

                    // Grab all data and convert to more useful object types
                    UUID uuid = UUID.fromString((String) o[0]);
                    String ip = (String) o[1];
                    long count = ((Number) o[2]).longValue();
                    String server = (String) o[3];
                    long created = ((Timestamp) o[4]).getTime();
                    long updated = ((Timestamp) o[5]).getTime();

                    data.add(new PlayerData(uuid, ip, count, server, created, updated));

                    // Update SQL data, force this thread
                    update(sql, storageConfigNode, uuid, ip, server).get();
                }
            } catch (SQLException | ClassCastException | ExecutionException ex) {
                logger.error(ex.getMessage(), ex);
            } catch (InterruptedException ex) {
                logger.error(ex.getMessage(), ex);
                Thread.currentThread().interrupt();
            }

            try {
                sql.execute("DELETE FROM `" + tablePrefix + "queue` WHERE `updated` <= CURRENT_TIMESTAMP() - INTERVAL 2 MINUTE;");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return new SQLFetchResult(data.toArray(new PlayerData[0]), removedKeys.toArray(new String[0]));
        });
    }

    public static CompletableFuture<Set<PlayerData>> getData(String ip, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "altfinder_";

        return CompletableFuture.supplyAsync(() -> {
            Set<PlayerData> data = new HashSet<>();

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `count`, `server`, `created`, `updated` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `ip`=?;", ip);

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Grab all data and convert to more useful object types
                    UUID uuid = UUID.fromString((String) o[0]);
                    long count = ((Number) o[1]).longValue();
                    String server = (String) o[2];
                    long created = ((Timestamp) o[3]).getTime();
                    long updated = ((Timestamp) o[4]).getTime();

                    data.add(new PlayerData(uuid, ip, count, server, created, updated));
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return data;
        });
    }

    public static CompletableFuture<Set<PlayerData>> getData(UUID uuid, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "altfinder_";

        return CompletableFuture.supplyAsync(() -> {
            Set<PlayerData> data = new HashSet<>();

            try {
                SQLQueryResult query = sql.query("SELECT `ip`, `count`, `server`, `created`, `updated` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `uuid`=?;", uuid.toString());

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Grab all data and convert to more useful object types
                    String ip = (String) o[0];
                    long count = ((Number) o[1]).longValue();
                    String server = (String) o[2];
                    long created = ((Timestamp) o[3]).getTime();
                    long updated = ((Timestamp) o[4]).getTime();

                    data.add(new PlayerData(uuid, ip, count, server, created, updated));
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return data;
        });
    }

    public static CompletableFuture<PlayerData> update(SQL sql, ConfigurationNode storageConfigNode, UUID uuid, String ip, String server) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "altfinder_";

        return CompletableFuture.supplyAsync(() -> {
            PlayerData result = null;

            try {
                sql.execute("INSERT INTO `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` (`uuid`, `ip`, `server`) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE `count`=`count`+1, `server`=?, `updated`=CURRENT_TIMESTAMP();", uuid.toString(), ip, server, server);
                SQLQueryResult query = sql.query("SELECT `count`, `created`, `updated` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

                Timestamp created = null;
                Timestamp updated = null;

                for (Object[] o : query.getData()) {
                    long count = ((Number) o[0]).longValue();
                    created = (Timestamp) o[1];
                    updated = (Timestamp) o[2];
                    result = new PlayerData(uuid, ip, count, server, created.getTime(), updated.getTime());
                }

                if (created != null) {
                    sql.execute("INSERT INTO `" + tablePrefix + "queue` (`uuid`, `ip`, `server`, `created`, `updated`) VALUES(?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `server`=?, `updated`=?;", uuid.toString(), ip, server, created, updated, server, updated);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return result;
        });
    }

    public static CompletableFuture<Void> delete(String search, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "altfinder_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("DELETE FROM `" + tablePrefix + "queue` WHERE `ip`=? OR `uuid`=?;", search, search);
                sql.execute("DELETE FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `ip`=? OR `uuid`=?;", search, search);
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Long> getCurrentTime(SQL sql) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT CURRENT_TIMESTAMP();");

                for (Object[] o : query.getData()) {
                    return ((Timestamp) o[0]).getTime();
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return -1L;
        });
    }
}
