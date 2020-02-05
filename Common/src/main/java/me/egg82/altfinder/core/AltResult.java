package me.egg82.altfinder.core;

import java.util.Objects;
import java.util.UUID;

public class AltResult {
    private final long id;
    private final String ip;
    private final UUID playerID;
    private final UUID serverID;
    private final String serverName;
    private final long count;
    private final long created;
    private final long updated;

    private final int hc;

    public AltResult(long id, String ip, UUID playerID, UUID serverID, String serverName, long count, long created, long updated) {
        this.id = id;
        this.ip = ip;
        this.playerID = playerID;
        this.serverID = serverID;
        this.serverName = serverName;
        this.count = count;
        this.created = created;
        this.updated = updated;

        hc = Objects.hash(id);
    }

    public long getID() { return id; }

    public String getIP() { return ip; }

    public UUID getPlayerID() { return playerID; }

    public UUID getServerID() { return serverID; }

    public String getServerName() { return serverName; }

    public long getCount() { return count; }

    public long getCreated() { return created; }

    public long getUpdated() { return updated; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AltResult)) return false;
        AltResult that = (AltResult) o;
        return id == that.id;
    }

    public int hashCode() { return hc; }
}
