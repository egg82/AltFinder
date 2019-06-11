package me.egg82.altfinder.core;

import java.util.Set;

public class DataInfoContainer {
    private final Set<PlayerInfoContainer> info;

    private long sqlTime = -1L;

    public DataInfoContainer(Set<PlayerInfoContainer> info) { this.info = info; }

    public Set<PlayerInfoContainer> getInfo() { return info; }

    public DataInfoContainer setSQLTime(long sqlTime) {
        this.sqlTime = sqlTime;
        return this;
    }

    public long getSQLTime() { return sqlTime; }
}
