package me.egg82.altfinder.core;

import java.util.Objects;
import java.util.UUID;

public class PostAltResult {
    private final long id;
    private final long ipID;
    private final String ip;
    private final long longPlayerID;
    private final UUID playerID;
    private final long longServerID;
    private final UUID serverID;
    private final String serverName;
    private final long count;
    private final long created;
    private final long updated;

    private final int hc;

    public PostAltResult(long id, long ipID, String ip, long longPlayerID, UUID playerID, long longServerID, UUID serverID, String serverName, long count, long created, long updated) {
        this.id = id;
        this.ipID = ipID;
        this.ip = ip;
        this.longPlayerID = longPlayerID;
        this.playerID = playerID;
        this.longServerID = longServerID;
        this.serverID = serverID;
        this.serverName = serverName;
        this.count = count;
        this.created = created;
        this.updated = updated;

        hc = Objects.hash(id);
    }

    public long getID() { return id; }

    public long getIPID() { return ipID; }

    public String getIP() { return ip; }

    public long getLongPlayerID() { return longPlayerID; }

    public UUID getPlayerID() { return playerID; }

    public long getLongServerID() { return longServerID; }

    public UUID getServerID() { return serverID; }

    public String getServerName() { return serverName; }

    public long getCount() { return count; }

    public long getCreated() { return created; }

    public long getUpdated() { return updated; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostAltResult)) return false;
        PostAltResult that = (PostAltResult) o;
        return id == that.id;
    }

    public int hashCode() { return hc; }
}
