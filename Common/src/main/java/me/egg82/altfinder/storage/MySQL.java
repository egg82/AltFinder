package me.egg82.altfinder.storage;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.mysql.cj.exceptions.MysqlErrorNumbers;
import com.zaxxer.hikari.HikariConfig;
import java.io.IOException;
import java.io.StringReader;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import me.egg82.altfinder.core.*;
import me.egg82.altfinder.services.StorageHandler;
import me.egg82.altfinder.utils.ValidationUtil;
import ninja.egg82.core.SQLExecuteResult;
import ninja.egg82.core.SQLQueryResult;
import ninja.egg82.sql.SQL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MySQL extends AbstractSQL {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final LoadingCache<String, Long> longIPIDCache = Caffeine.newBuilder().build(this::getLongIPIDExpensive);
    private final LoadingCache<UUID, Long> longPlayerIDCache = Caffeine.newBuilder().build(this::getLongPlayerIDExpensive);

    private String serverName;
    private String serverID;
    private UUID uuidServerID;
    private long longServerID;
    private volatile long lastAltID;
    private StorageHandler handler;

    private MySQL() { }

    private volatile boolean closed = false;

    public void close() {
        closed = true;
        sql.close();
    }

    public boolean isClosed() { return closed || sql.isClosed(); }

    public static MySQL.Builder builder(UUID serverID, String serverName, StorageHandler handler) { return new MySQL.Builder(serverID, serverName, handler); }

    public static class Builder {
        private final MySQL result = new MySQL();
        private final HikariConfig config = new HikariConfig();

        private Builder(UUID serverID, String serverName, StorageHandler handler) {
            if (serverID == null) {
                throw new IllegalArgumentException("serverID cannot be null.");
            }
            if (serverName == null) {
                throw new IllegalArgumentException("serverName cannot be null.");
            }
            if (handler == null) {
                throw new IllegalArgumentException("handler cannot be null.");
            }

            result.uuidServerID = serverID;
            result.serverID = serverID.toString();
            result.serverName = serverName;
            result.handler = handler;

            // Baseline
            config.setConnectionTestQuery("SELECT 1;");
            config.setAutoCommit(true);
            config.addDataSourceProperty("useLegacyDatetimeCode", "false");
            config.addDataSourceProperty("serverTimezone", "UTC");

            // Optimizations
            // http://assets.en.oreilly.com/1/event/21/Connector_J%20Performance%20Gems%20Presentation.pdf
            // https://webcache.googleusercontent.com/search?q=cache:GqZCOIZxeK0J:assets.en.oreilly.com/1/event/21/Connector_J%2520Performance%2520Gems%2520Presentation.pdf+&cd=1&hl=en&ct=clnk&gl=us
            config.addDataSourceProperty("cacheServerConfiguration", "true");
            config.addDataSourceProperty("useLocalSessionState", "true");
            config.addDataSourceProperty("useLocalTransactionState", "true");
            config.addDataSourceProperty("rewriteBatchedStatements", "true");
            config.addDataSourceProperty("useServerPrepStmts", "true");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("maintainTimeStats", "false");
            config.addDataSourceProperty("useUnbufferedIO", "false");
            config.addDataSourceProperty("useReadAheadInput", "false");
            // https://github.com/brettwooldridge/HikariCP/wiki/MySQL-Configuration
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("cacheResultSetMetadata", "true");
            config.addDataSourceProperty("elideSetAutoCommits", "true");
        }

        public MySQL.Builder url(String address, int port, String database, String prefix) {
            config.setJdbcUrl("jdbc:mysql://" + address + ":" + port + "/" + database);
            result.database = database;
            result.prefix = prefix;
            return this;
        }

        public MySQL.Builder credentials(String user, String pass) {
            config.setUsername(user);
            config.setPassword(pass);
            return this;
        }

        public MySQL.Builder options(String options) throws IOException {
            options = !options.isEmpty() && options.charAt(0) == '?' ? options.substring(1) : options;
            Properties p = new Properties();
            p.load(new StringReader(options.replace("&", "\n")));
            config.setDataSourceProperties(p);
            return this;
        }

        public MySQL.Builder poolSize(int min, int max) {
            config.setMaximumPoolSize(max);
            config.setMinimumIdle(min);
            return this;
        }

        public MySQL.Builder life(long lifetime, long timeout) {
            config.setMaxLifetime(lifetime);
            config.setConnectionTimeout(timeout);
            return this;
        }

        public MySQL build() throws IOException, StorageException {
            result.sql = new SQL(config);
            SQLVersionUtil.conformVersion(result, "mysql");
            result.longServerID = getLongServerID();
            result.lastAltID = getLastAltID();
            return result;
        }

        private long getLongServerID() throws StorageException {
            SQLQueryResult r;
            try {
                r = result.sql.query("SELECT `id` FROM `" + result.prefix + "servers` WHERE `uuid`=?;", result.serverID);
            } catch (SQLException ex) {
                throw new StorageException(false, ex);
            }
            if (r.getData().length != 1) {
                throw new StorageException(false, "Could not get server ID.");
            }
            return ((Number) r.getData()[0][0]).longValue();
        }

        private long getLastAltID() throws StorageException {
            SQLQueryResult r;
            try {
                r = result.sql.query("SELECT MAX(`id`) FROM `" + result.prefix + "alts`;");
            } catch (SQLException ex) {
                throw new StorageException(false, ex);
            }
            if (r.getData().length != 1) {
                throw new StorageException(false, "Could not get alt IDs.");
            }
            return r.getData()[0][0] != null ? ((Number) r.getData()[0][0]).longValue() : 0;
        }
    }

    public Set<AltResult> getQueue() throws StorageException {
        Set<AltResult> retVal = new LinkedHashSet<>();
        SQLQueryResult result;
        try {
            result = sql.call("call `" + prefix + "get_queue_id`(?);", lastAltID);
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
        for (Object[] row : result.getData()) {
            AltResult r = getResult(row);
            if (r != null) {
                lastAltID = r.getID();
                retVal.add(r);
            }
        }
        return retVal;
    }

    public Set<AltResult> getByIP(String ip, int days) throws StorageException {
        if (ip == null) {
            throw new IllegalArgumentException("ip cannot be null.");
        }
        if (!ValidationUtil.isValidIp(ip)) {
            throw new IllegalArgumentException("ip is invalid.");
        }

        long longIPID = longIPIDCache.get(ip);
        Set<AltResult> retVal = new LinkedHashSet<>();
        SQLQueryResult result;
        try {
            result = sql.call("call `" + prefix + "get_alts_ip`(?, ?);", longIPID, days);
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
        if (result.getData().length == 1) {
            retVal.add(getResult(result.getData()[0]));
        }
        retVal.remove(null);
        return retVal;
    }

    public Set<AltResult> getByPlayer(UUID playerID, int days) throws StorageException {
        if (playerID == null) {
            throw new IllegalArgumentException("playerID cannot be null.");
        }

        long longPlayerID = longPlayerIDCache.get(playerID);
        Set<AltResult> retVal = new LinkedHashSet<>();
        SQLQueryResult result;
        try {
            result = sql.call("call `" + prefix + "get_alts_player`(?, ?);", longPlayerID, days);
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
        if (result.getData().length == 1) {
            retVal.add(getResult(result.getData()[0]));
        }
        retVal.remove(null);
        return retVal;
    }

    public PostAltResult post(UUID playerID, String ip) throws StorageException {
        if (playerID == null) {
            throw new IllegalArgumentException("playerID cannot be null.");
        }
        if (ip == null) {
            throw new IllegalArgumentException("ip cannot be null.");
        }
        if (!ValidationUtil.isValidIp(ip)) {
            throw new IllegalArgumentException("ip is invalid.");
        }

        long longIPID = longIPIDCache.get(ip);
        long longPlayerID = longPlayerIDCache.get(playerID);
        try {
            sql.execute("INSERT INTO `" + prefix + "alts` (`ip_id`, `player_id`, `server_id`) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE `server_id`=?, `count`=`count` + 1, `updated`=CURRENT_TIMESTAMP();", longIPID, longPlayerID, longServerID, longServerID);
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }

        SQLQueryResult query;
        try {
            query = sql.query("SELECT `id`, `count`, `created`, `updated` FROM `" + prefix + "alts` WHERE `ip_id`=? AND `player_id`=?;", longIPID, longPlayerID);
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
        if (query.getData().length != 1) {
            throw new StorageException(false, "Could not get data from inserted value.");
        }

        return new PostAltResult(
                ((Number) query.getData()[0][0]).longValue(),
                longIPID,
                ip,
                longPlayerID,
                playerID,
                longServerID,
                uuidServerID,
                serverName,
                ((Number) query.getData()[0][1]).longValue(),
                ((Timestamp) query.getData()[0][2]).getTime(),
                ((Timestamp) query.getData()[0][3]).getTime()
        );
    }

    public void setServerRaw(long longServerID, UUID serverID, String name) throws StorageException {
        try {
            sql.execute("INSERT INTO `" + prefix + "servers` (`id`, `uuid`, `name`) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE `id`=?, `uuid`=?, `name`=?;", longServerID, serverID.toString(), name, longServerID, serverID.toString(), name);
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
    }

    public void setIPRaw(long longIPID, String ip) throws StorageException {
        try {
            sql.execute("INSERT INTO `" + prefix + "ips` (`id`, `ip`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `ip`=?, `uuid`=?;", longIPID, ip, longIPID, ip);
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
        longIPIDCache.put(ip, longIPID);
    }

    public void setPlayerRaw(long longPlayerID, UUID playerID) throws StorageException {
        try {
            sql.execute("INSERT INTO `" + prefix + "players` (`id`, `uuid`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `id`=?, `uuid`=?;", longPlayerID, playerID.toString(), longPlayerID, playerID.toString());
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
        longPlayerIDCache.put(playerID, longPlayerID);
    }

    public void postRaw(long id, long longIPID, long longPlayerID, long longServerID, long count, long created, long updated) throws StorageException {
        try {
            sql.execute("INSERT IGNORE INTO `" + prefix + "alts` (`id`, `ip_id`, `player_id`, `server_id`, `count`, `created`, `updated`) VALUES (?, ?, ?, ?, ?, ?, ?);", id, longIPID, longPlayerID, longServerID, count, new Timestamp(created), new Timestamp(updated));
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
    }

    public void setServerName(String name) throws StorageException {
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null.");
        }
        // Don't redirect to raw. Will cause issues when server is first added
        try {
            sql.execute("INSERT INTO `" + prefix + "servers` (`uuid`, `name`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `name`=?;", serverID, name, name);
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
    }

    protected void setKey(String key, String value) throws SQLException { sql.execute("INSERT INTO `" + prefix + "data` (`key`, `value`) VALUES (?, ?) ON DUPLICATE KEY UPDATE `value`=?;", key, value, value); }

    protected double getDouble(String key) throws SQLException {
        SQLQueryResult result = sql.query("SELECT `value` FROM `" + prefix + "data` WHERE `key`=?;", key);
        if (result.getData().length == 1) {
            return Double.parseDouble((String) result.getData()[0][0]);
        }
        return -1.0d;
    }

    public long getLongIPID(String ip) { return longIPIDCache.get(ip); }

    public long getLongPlayerID(UUID playerID) { return longPlayerIDCache.get(playerID); }

    public Set<ServerResult> dumpServers() throws StorageException {
        Set<ServerResult> retVal = new LinkedHashSet<>();

        SQLQueryResult result;
        try {
            result = sql.query("SELECT `id`, `uuid`, `name` FROM `" + prefix + "servers`;");
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }

        for (Object[] row : result.getData()) {
            String sid = (String) row[1];
            if (!ValidationUtil.isValidUuid(sid)) {
                logger.warn("Server ID " + ((Number) row[0]).longValue() + " has an invalid UUID \"" + sid + "\".");
                continue;
            }

            retVal.add(new ServerResult(
                    ((Number) row[0]).longValue(),
                    UUID.fromString(sid),
                    (String) row[2]
            ));
        }

        return retVal;
    }

    public void loadServers(Set<ServerResult> servers) throws StorageException {
        // TODO: Batch execute
        try {
            sql.execute("SET FOREIGN_KEY_CHECKS = 0;");
            sql.execute("TRUNCATE `" + prefix + "servers`;");
            for (ServerResult server : servers) {
                sql.execute("INSERT INTO `" + prefix + "servers` (`id`, `uuid`, `name`) VALUES (?, ?, ?);", server.getLongServerID(), server.getServerID().toString(), server.getName());
            }
            sql.execute("SET FOREIGN_KEY_CHECKS = 1;");
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
    }

    public Set<IPResult> dumpIPs(long begin, int size) throws StorageException {
        Set<IPResult> retVal = new LinkedHashSet<>();

        SQLQueryResult result;
        try {
            result = sql.query("SELECT `id`, `ip` FROM `" + prefix + "ips` LIMIT ?, ?;", begin - 1, size);
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }

        for (Object[] row : result.getData()) {
            String ip = (String) row[1];
            if (!ValidationUtil.isValidIp(ip)) {
                logger.warn("IP ID " + ((Number) row[0]).longValue() + " has an invalid IP \"" + ip + "\".");
                continue;
            }

            retVal.add(new IPResult(
                    ((Number) row[0]).byteValue(),
                    (String) row[1]
            ));
        }

        return retVal;
    }

    public void loadIPs(Set<IPResult> ips, boolean truncate) throws StorageException {
        // TODO: Batch execute
        try {
            if (truncate) {
                sql.execute("SET FOREIGN_KEY_CHECKS = 0;");
                sql.execute("TRUNCATE `" + prefix + "ips`;");
                longIPIDCache.invalidateAll();
            }
            for (IPResult ip : ips) {
                sql.execute("INSERT INTO `" + prefix + "ips` (`id`, `ip`) VALUES (?, ?);", ip.getLongIPID(), ip.getIP());
                longIPIDCache.put(ip.getIP(), ip.getLongIPID());
            }
            if (truncate) {
                sql.execute("SET FOREIGN_KEY_CHECKS = 1;");
            }
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
    }

    public Set<PlayerResult> dumpPlayers(long begin, int size) throws StorageException {
        Set<PlayerResult> retVal = new LinkedHashSet<>();

        SQLQueryResult result;
        try {
            result = sql.query("SELECT `id`, `uuid` FROM `" + prefix + "players` LIMIT ?, ?;", begin - 1, size);
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }

        for (Object[] row : result.getData()) {
            String pid = (String) row[1];
            if (!ValidationUtil.isValidUuid(pid)) {
                logger.warn("Player ID " + ((Number) row[0]).longValue() + " has an invalid UUID \"" + pid + "\".");
                continue;
            }

            retVal.add(new PlayerResult(
                    ((Number) row[0]).longValue(),
                    UUID.fromString(pid)
            ));
        }

        return retVal;
    }

    public void loadPlayers(Set<PlayerResult> players, boolean truncate) throws StorageException {
        // TODO: Batch execute
        try {
            if (truncate) {
                sql.execute("SET FOREIGN_KEY_CHECKS = 0;");
                sql.execute("TRUNCATE `" + prefix + "players`;");
                longPlayerIDCache.invalidateAll();
            }
            for (PlayerResult player : players) {
                sql.execute("INSERT INTO `" + prefix + "players` (`id`, `uuid`) VALUES (?, ?);", player.getLongPlayerID(), player.getPlayerID().toString());
                longPlayerIDCache.put(player.getPlayerID(), player.getLongPlayerID());
            }
            if (truncate) {
                sql.execute("SET FOREIGN_KEY_CHECKS = 1;");
            }
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
    }

    public Set<RawAltResult> dumpAltValues(long begin, int size) throws StorageException {
        Set<RawAltResult> retVal = new LinkedHashSet<>();

        SQLQueryResult result;
        try {
            result = sql.query("SELECT `id`, `ip_id`, `player_id`, `server_id`, `count`, `created`, `updated` FROM `" + prefix + "alts` LIMIT ?, ?;", begin - 1, size);
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }

        for (Object[] row : result.getData()) {
            retVal.add(new RawAltResult(
                    ((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue(),
                    ((Number) row[4]).longValue(),
                    ((Timestamp) row[5]).getTime(),
                    ((Timestamp) row[6]).getTime()
            ));
        }

        return retVal;
    }

    public void loadAltValues(Set<RawAltResult> values, boolean truncate) throws StorageException {
        // TODO: Batch execute
        try {
            if (truncate) {
                sql.execute("SET FOREIGN_KEY_CHECKS = 0;");
                sql.execute("TRUNCATE `" + prefix + "alts`;");
            }
            for (RawAltResult value : values) {
                sql.execute("INSERT INTO `" + prefix + "alts` (`id`, `ip_id`, `player_id`, `server_id`, `count`, `created`, `updated`) VALUES (?, ?, ?, ?, ?, ?, ?);", value.getID(), value.getIPID(), value.getLongPlayerID(), value.getLongPlayerID(), value.getCount(), new Timestamp(value.getCreated()), new Timestamp(value.getUpdated()));
            }
            if (truncate) {
                sql.execute("SET FOREIGN_KEY_CHECKS = 1;");
            }
        } catch (SQLException ex) {
            throw new StorageException(isAutomaticallyRecoverable(ex), ex);
        }
    }

    private AltResult getResult(Object[] row) {
        String ip = (String) row[1];
        if (!ValidationUtil.isValidIp(ip)) {
            logger.warn("Alt ID " + row[0] + " has an invalid IP \"" + row[1] + "\".");
            return null;
        }

        String playerID = (String) row[2];
        if (!ValidationUtil.isValidIp(playerID)) {
            logger.warn("Alt ID " + row[0] + " has an invalid player ID \"" + row[2] + "\".");
            return null;
        }

        String serverID = (String) row[3];
        if (!ValidationUtil.isValidIp(serverID)) {
            logger.warn("Alt ID " + row[0] + " has an invalid server ID \"" + row[3] + "\".");
            return null;
        }

        return new AltResult(
                ((Number) row[0]).longValue(),
                ip,
                UUID.fromString(playerID),
                UUID.fromString(serverID),
                (String) row[4],
                ((Number) row[5]).longValue(),
                ((Timestamp) row[6]).getTime(),
                ((Timestamp) row[7]).getTime()
        );
    }

    private long getLongIPIDExpensive(String ip) throws SQLException, StorageException {
        // A majority of the time there'll be an ID
        SQLQueryResult result = sql.query("SELECT `id` FROM `" + prefix + "ips` WHERE `ip`=?;", ip);
        if (result.getData().length == 1) {
            return ((Number) result.getData()[0][0]).longValue();
        }

        // No ID, generate one
        SQLExecuteResult r = sql.execute("INSERT INTO `" + prefix + "ips` (`ip`) VALUES (?);", ip);
        if (r.getAutoGeneratedKeys().length != 1) {
            throw new StorageException(false, "Could not get generated keys from inserted IP.");
        }
        long id = ((Number) r.getAutoGeneratedKeys()[0]).longValue();
        handler.ipIDCreationCallback(ip, id, this);
        return id;
    }

    private long getLongPlayerIDExpensive(UUID uuid) throws SQLException, StorageException {
        // A majority of the time there'll be an ID
        SQLQueryResult result = sql.query("SELECT `id` FROM `" + prefix + "players` WHERE `uuid`=?;", uuid.toString());
        if (result.getData().length == 1) {
            return ((Number) result.getData()[0][0]).longValue();
        }

        // No ID, generate one
        SQLExecuteResult r = sql.execute("INSERT INTO `" + prefix + "players` (`uuid`) VALUES (?);", uuid.toString());
        if (r.getAutoGeneratedKeys().length != 1) {
            throw new StorageException(false, "Could not get generated keys from inserted player.");
        }
        long id = ((Number) r.getAutoGeneratedKeys()[0]).longValue();
        handler.playerIDCreationCallback(uuid, id, this);
        return id;
    }

    protected boolean isAutomaticallyRecoverable(SQLException ex) {
        if (
                ex.getErrorCode() == MysqlErrorNumbers.ER_LOCK_WAIT_TIMEOUT
                || ex.getErrorCode() == MysqlErrorNumbers.ER_QUERY_TIMEOUT
                || ex.getErrorCode() == MysqlErrorNumbers.ER_CON_COUNT_ERROR
                || ex.getErrorCode() == MysqlErrorNumbers.ER_TOO_MANY_DELAYED_THREADS
                || ex.getErrorCode() == MysqlErrorNumbers.ER_BINLOG_PURGE_EMFILE
                || ex.getErrorCode() == MysqlErrorNumbers.ER_TOO_MANY_CONCURRENT_TRXS
                || ex.getErrorCode() == MysqlErrorNumbers.ER_OUTOFMEMORY
                || ex.getErrorCode() == MysqlErrorNumbers.ER_OUT_OF_SORTMEMORY
                || ex.getErrorCode() == MysqlErrorNumbers.ER_CANT_CREATE_THREAD
                || ex.getErrorCode() == MysqlErrorNumbers.ER_OUT_OF_RESOURCES
                || ex.getErrorCode() == MysqlErrorNumbers.ER_ENGINE_OUT_OF_MEMORY
        ) {
            return true;
        }
        return false;
    }
}
