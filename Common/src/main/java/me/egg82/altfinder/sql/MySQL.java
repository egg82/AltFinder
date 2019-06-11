package me.egg82.altfinder.sql;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import me.egg82.altfinder.APIException;
import me.egg82.altfinder.core.PlayerData;
import me.egg82.altfinder.core.SQLFetchResult;
import me.egg82.altfinder.extended.CachedConfigValues;
import me.egg82.altfinder.utils.ConfigUtil;
import me.egg82.altfinder.utils.ValidationUtil;
import ninja.egg82.core.SQLQueryResult;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MySQL {
    private static final Logger logger = LoggerFactory.getLogger(MySQL.class);

    private MySQL() {}

    public static void createTables() throws APIException, SQLException {
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        if (!tableExists(tablePrefix.substring(0, tablePrefix.length() - 1))) {
            cachedConfig.get().getSQL().execute("CREATE TABLE `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` ("
                    + "`ip` VARCHAR(45) NOT NULL,"
                    + "`uuid` VARCHAR(36) NOT NULL,"
                    + "`count` BIGINT UNSIGNED NOT NULL DEFAULT 0,"
                    + "`server` VARCHAR(255) NOT NULL,"
                    + "`created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ");");
            cachedConfig.get().getSQL().execute("ALTER TABLE `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` ADD UNIQUE (`ip`, `uuid`);");
        }

        if (!tableExists(tablePrefix + "queue")) {
            cachedConfig.get().getSQL().execute("CREATE TABLE `" + tablePrefix + "queue` ("
                    + "`ip` VARCHAR(45) NOT NULL,"
                    + "`uuid` VARCHAR(36) NOT NULL,"
                    + "`count` BIGINT UNSIGNED NOT NULL DEFAULT 0,"
                    + "`server` VARCHAR(255) NOT NULL,"
                    + "`created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ");");
            cachedConfig.get().getSQL().execute("ALTER TABLE `" + tablePrefix + "queue` ADD UNIQUE (`ip`, `uuid`);");
        }
    }

    public static SQLFetchResult loadInfo() throws APIException, SQLException {
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        List<PlayerData> data = new ArrayList<>();
        List<String> removedKeys = new ArrayList<>();

        try {
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid`, `ip`, `count`, `server`, `created`, `updated` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "`;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("altfndr:uuid:" + o[0]);
                    removedKeys.add("altfndr:info:" + o[0] + "|" + o[1]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `uuid`=?;", o[0]);
                    continue;
                }
                if (!ValidationUtil.isValidIp((String) o[1])) {
                    removedKeys.add("altfndr:ip:" + o[1]);
                    removedKeys.add("altfndr:info:" + o[0] + "|" + o[1]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `ip`=?;", o[1]);
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
        } catch (ClassCastException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return new SQLFetchResult(
                data.toArray(new PlayerData[0]),
                removedKeys.toArray(new String[0])
        );
    }

    public static SQLFetchResult fetchQueue() throws APIException, SQLException {
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        List<PlayerData> data = new ArrayList<>();
        List<String> removedKeys = new ArrayList<>();

        try {
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid`, `ip`, `count`, `server`, `created`, `updated` FROM `" + tablePrefix + "queue` ORDER BY `updated` ASC;");

            // Iterate rows
            for (Object[] o : query.getData()) {
                // Validate UUID/IP and remove bad data
                if (!ValidationUtil.isValidUuid((String) o[0])) {
                    removedKeys.add("altfndr:uuid:" + o[0]);
                    removedKeys.add("altfndr:info:" + o[0] + "|" + o[1]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "queue` WHERE `uuid`=?;", o[0]);
                    continue;
                }
                if (!ValidationUtil.isValidIp((String) o[1])) {
                    removedKeys.add("altfndr:ip:" + o[1]);
                    removedKeys.add("altfndr:info:" + o[0] + "|" + o[1]);
                    cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "queue` WHERE `ip`=?;", o[1]);
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
        } catch (ClassCastException ex) {
            logger.error(ex.getMessage(), ex);
        }

        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "queue` WHERE `updated` <= CURRENT_TIMESTAMP() - INTERVAL 2 MINUTE;");

        return new SQLFetchResult(data.toArray(new PlayerData[0]), removedKeys.toArray(new String[0]));
    }

    public static Optional<Set<PlayerData>> getData(String ip) throws APIException, SQLException {
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        Set<PlayerData> data = new HashSet<>();

        try {
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `uuid`, `count`, `server`, `created`, `updated` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `ip`=?;", ip);

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
        } catch (ClassCastException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return data.isEmpty() ? Optional.empty() : Optional.of(data);
    }

    public static Optional<Set<PlayerData>> getData(UUID uuid) throws APIException, SQLException {
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        Set<PlayerData> data = new HashSet<>();

        try {
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `ip`, `count`, `server`, `created`, `updated` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `uuid`=?;", uuid.toString());

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
        } catch (ClassCastException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return data.isEmpty() ? Optional.empty() : Optional.of(data);
    }

    public static PlayerData update(UUID uuid, String ip, String server) throws APIException, SQLException {
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        PlayerData result = null;

        try {
            cachedConfig.get().getSQL().execute("INSERT INTO `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` (`uuid`, `ip`, `server`) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE `count`=`count`+1, `server`=?, `updated`=CURRENT_TIMESTAMP();", uuid.toString(), ip, server, server);
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `count`, `created`, `updated` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

            Timestamp created = null;
            Timestamp updated = null;

            for (Object[] o : query.getData()) {
                long count = ((Number) o[0]).longValue();
                created = (Timestamp) o[1];
                updated = (Timestamp) o[2];
                result = new PlayerData(uuid, ip, count, server, created.getTime(), updated.getTime());
            }

            if (created != null) {
                cachedConfig.get().getSQL().execute("INSERT INTO `" + tablePrefix + "queue` (`uuid`, `ip`, `server`, `created`, `updated`) VALUES(?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `server`=?, `updated`=?;", uuid.toString(), ip, server, created, updated, server, updated);
            }
        } catch (ClassCastException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return result;
    }

    public static void delete(String search) throws APIException, SQLException {
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix + "queue` WHERE `ip`=? OR `uuid`=?;", search, search);
        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `ip`=? OR `uuid`=?;", search, search);
    }

    public static long getCurrentTime() throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        try {
            long start = System.currentTimeMillis();
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT CURRENT_TIMESTAMP();");

            for (Object[] o : query.getData()) {
                return ((Timestamp) o[0]).getTime() + (System.currentTimeMillis() - start);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        throw new APIException(true, "Could not get time from SQL.");
    }

    private static boolean tableExists(String tableName) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        ConfigurationNode storageConfigNode = ConfigUtil.getStorageNodeOrNull();

        if (!cachedConfig.isPresent() || storageConfigNode == null) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String databaseName = storageConfigNode.getNode("data", "database").getString();

        SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?;", databaseName, tableName);
        return query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0;
    }

    private static String getTablePrefix() throws APIException {
        ConfigurationNode storageConfigNode = ConfigUtil.getStorageNodeOrNull();

        if (storageConfigNode == null) {
            throw new APIException(true, "Could not get required configuration.");
        }

        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "altfinder_";
        if (tablePrefix.charAt(tablePrefix.length() - 1) != '_') {
            tablePrefix = tablePrefix + "_";
        }
        return tablePrefix;
    }
}
