package me.egg82.altfinder.sql;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.core.SQLFetchResult;
import me.egg82.altfinder.utils.ValidationUtil;
import ninja.egg82.core.SQLQueryResult;
import ninja.egg82.sql.SQL;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SQLite {
    private static final Logger logger = LoggerFactory.getLogger(SQLite.class);

    private SQLite() {}

    public static CompletableFuture<Void> createTables(SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "altfinder_";

        return CompletableFuture.runAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + tablePrefix.substring(0, tablePrefix.length() - 1) + "';");
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` ("
                        + "`uuid` TEXT(36) NOT NULL,"
                        + "`ip` TEXT(45) NOT NULL,"
                        + "`count` UNSIGNED INT NOT NULL DEFAULT 0,"
                        + "`server` TEXT(255) NOT NULL,"
                        + "`created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE(`ip`, `uuid`)"
                        + ");");
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
                    long created = getTime(o[4]).getTime();
                    long updated = getTime(o[5]).getTime();

                    data.add(new PlayerData(uuid, ip, count, server, created, updated));
                }
            } catch (SQLException | ClassCastException ex) {
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
                    long created = getTime(o[3]).getTime();
                    long updated = getTime(o[4]).getTime();

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
                    long created = getTime(o[3]).getTime();
                    long updated = getTime(o[4]).getTime();

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
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` (`uuid`, `ip`, `count`, `server`, `created`) VALUES (?, ?, (SELECT `count` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `uuid`=? AND `ip`=?) + 1, ?, (SELECT `created` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `uuid`=? AND `ip`=?));", uuid.toString(), ip, uuid.toString(), ip, server, uuid.toString(), ip);
                SQLQueryResult query = sql.query("SELECT `count`, `created`, `updated` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

                for (Object[] o : query.getData()) {
                    long count = ((Number) o[0]).longValue();
                    Timestamp created = getTime(o[1]);
                    Timestamp updated = getTime(o[2]);
                    result = new PlayerData(uuid, ip, count, server, created.getTime(), updated.getTime());
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return result;
        });
    }

    public static CompletableFuture<Void> add(PlayerData data, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "altfinder_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` (`uuid`, `ip`, `count`, `server`, `created`, `updated`) VALUES (?, ?, ?, ?, ?, ?);", data.getUUID().toString(), data.getIP(), data.getCount(), data.getServer(), data.getCreated(), data.getUpdated());
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Void> delete(String search, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "altfinder_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("DELETE FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `ip`=? OR `uuid`=?;", search, search);
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Long> getCurrentTime(SQL sql) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT CURRENT_TIMESTAMP;");

                for (Object[] o : query.getData()) {
                    return getTime(o[0]).getTime();
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return -1L;
        });
    }

    private static Timestamp getTime(Object o) {
        if (o instanceof String) {
            return Timestamp.valueOf((String) o);
        } else if (o instanceof Number) {
            return new Timestamp(((Number) o).longValue());
        }
        return null;
    }
}
