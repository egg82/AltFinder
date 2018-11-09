package me.egg82.altfinder.extended;

import com.google.common.collect.ImmutableSet;
import com.rabbitmq.client.ConnectionFactory;
import java.util.Collection;
import me.egg82.altfinder.enums.SQLType;
import ninja.egg82.sql.SQL;
import redis.clients.jedis.JedisPool;

public class CachedConfigValues {
    private CachedConfigValues() {}

    private boolean debug = false;
    public boolean getDebug() { return debug; }

    private ImmutableSet<String> ignored = ImmutableSet.of();
    public ImmutableSet<String> getIgnored() { return ignored; }

    private JedisPool redisPool = null;
    public JedisPool getRedisPool() { return redisPool; }

    private ConnectionFactory rabbitConnectionFactory = null;
    public ConnectionFactory getRabbitConnectionFactory() { return rabbitConnectionFactory; }

    private SQL sql = null;
    public SQL getSQL() { return sql; }

    private SQLType sqlType = SQLType.SQLite;
    public SQLType getSQLType() { return sqlType; }

    public static CachedConfigValues.Builder builder() { return new CachedConfigValues.Builder(); }

    public static class Builder {
        private final CachedConfigValues values = new CachedConfigValues();

        private Builder() {}

        public CachedConfigValues.Builder debug(boolean value) {
            values.debug = value;
            return this;
        }

        public CachedConfigValues.Builder ignored(Collection<String> value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }
            values.ignored = ImmutableSet.copyOf(value);
            return this;
        }

        public CachedConfigValues.Builder redisPool(JedisPool value) {
            values.redisPool = value;
            return this;
        }

        public CachedConfigValues.Builder rabbitConnectionFactory(ConnectionFactory value) {
            values.rabbitConnectionFactory = value;
            return this;
        }

        public CachedConfigValues.Builder sql(SQL value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }
            values.sql = value;
            return this;
        }

        public CachedConfigValues.Builder sqlType(String value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }
            values.sqlType = SQLType.getByName(value);
            return this;
        }

        public CachedConfigValues build() {
            return values;
        }
    }
}
