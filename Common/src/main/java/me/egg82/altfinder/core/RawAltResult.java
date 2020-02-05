package me.egg82.altfinder.core;

import java.util.Objects;

public class RawAltResult {
    private final long id;
    private final long ipID;
    private final long longPlayerID;
    private final long longServerID;
    private final long count;
    private final long created;
    private final long updated;

    private final int hc;

    public RawAltResult(long id, long ipID, long longPlayerID, long serverID, long count, long created, long updated) {
        this.id = id;
        this.ipID = ipID;
        this.longPlayerID = longPlayerID;
        this.longServerID = serverID;
        this.count = count;
        this.created = created;
        this.updated = updated;

        hc = Objects.hash(id);
    }

    public long getID() { return id; }

    public long getIPID() { return ipID; }

    public long getLongPlayerID() { return longPlayerID; }

    public long getLongServerID() { return longServerID; }

    public long getCount() { return count; }

    public long getCreated() { return created; }

    public long getUpdated() { return updated; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RawAltResult)) return false;
        RawAltResult that = (RawAltResult) o;
        return id == that.id;
    }

    public int hashCode() { return hc; }
}
