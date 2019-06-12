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

public class SQLite {
    private static final Logger logger = LoggerFactory.getLogger(SQLite.class);

    private SQLite() {}

    public static void createTables() throws APIException, SQLException {
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        if (!tableExists(tablePrefix.substring(0, tablePrefix.length() - 1))) {
            cachedConfig.get().getSQL().execute("CREATE TABLE `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` ("
                    + "`uuid` TEXT(36) NOT NULL,"
                    + "`ip` TEXT(45) NOT NULL,"
                    + "`count` UNSIGNED INT NOT NULL DEFAULT 0,"
                    + "`server` TEXT(255) NOT NULL,"
                    + "`created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "`updated` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "UNIQUE(`ip`, `uuid`)"
                    + ");");
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
                long created = getTime(o[4]).getTime();
                long updated = getTime(o[5]).getTime();

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
                long created = getTime(o[3]).getTime();
                long updated = getTime(o[4]).getTime();

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
                long created = getTime(o[3]).getTime();
                long updated = getTime(o[4]).getTime();

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
            cachedConfig.get().getSQL().execute("INSERT OR REPLACE INTO `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` (`uuid`, `ip`, `count`, `server`, `created`) VALUES (?, ?, (SELECT `count` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `uuid`=? AND `ip`=?) + 1, ?, (SELECT `created` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `uuid`=? AND `ip`=?));", uuid.toString(), ip, uuid.toString(), ip, server, uuid.toString(), ip);
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT `count`, `created`, `updated` FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

            for (Object[] o : query.getData()) {
                long count = ((Number) o[0]).longValue();
                Timestamp created = getTime(o[1]);
                Timestamp updated = getTime(o[2]);
                result = new PlayerData(uuid, ip, count, server, created.getTime(), updated.getTime());
            }
        } catch (ClassCastException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return result;
    }

    public static void add(PlayerData data) throws APIException, SQLException {
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        cachedConfig.get().getSQL().execute("INSERT OR REPLACE INTO `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` (`uuid`, `ip`, `count`, `server`, `created`, `updated`) VALUES (?, ?, ?, ?, ?, ?);", data.getUUID().toString(), data.getIP(), data.getCount(), data.getServer(), data.getCreated(), data.getUpdated());
    }

    public static void delete(String search) throws APIException, SQLException {
        String tablePrefix = getTablePrefix();
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        cachedConfig.get().getSQL().execute("DELETE FROM `" + tablePrefix.substring(0, tablePrefix.length() - 1) + "` WHERE `ip`=? OR `uuid`=?;", search, search);
    }

    public static long getCurrentTime() throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        if (!cachedConfig.isPresent()) {
            throw new APIException(true, "Could not get required configuration.");
        }

        try {
            long start = System.currentTimeMillis();
            SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT CURRENT_TIMESTAMP;");

            for (Object[] o : query.getData()) {
                return getTime(o[0]).getTime() + (System.currentTimeMillis() - start);
            }
        } catch (ClassCastException ex) {
            throw new APIException(true, ex);
        }

        throw new APIException(true, "Could not get time from SQL.");
    }

    private static Timestamp getTime(Object o) throws APIException {
        if (o instanceof String) {
            return Timestamp.valueOf((String) o);
        } else if (o instanceof Number) {
            return new Timestamp(((Number) o).longValue());
        }
        throw new APIException(true, "Could not parse time.");
    }

    private static boolean tableExists(String tableName) throws APIException, SQLException {
        Optional<CachedConfigValues> cachedConfig = ConfigUtil.getCachedConfig();
        ConfigurationNode storageConfigNode = ConfigUtil.getStorageNodeOrNull();

        if (!cachedConfig.isPresent() || storageConfigNode == null) {
            throw new APIException(true, "Could not get required configuration.");
        }

        SQLQueryResult query = cachedConfig.get().getSQL().query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?;", tableName);
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
