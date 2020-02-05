package me.egg82.altfinder.core;

import java.util.Objects;
import java.util.UUID;

public class ServerResult {
    private final long longServerID;
    private final UUID serverID;
    private final String name;

    private final int hc;

    public ServerResult(long longServerID, UUID serverID, String name) {
        this.longServerID = longServerID;
        this.serverID = serverID;
        this.name = name;

        hc = Objects.hash(longServerID);
    }

    public long getLongServerID() { return longServerID; }

    public UUID getServerID() { return serverID; }

    public String getName() { return name; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerResult)) return false;
        ServerResult that = (ServerResult) o;
        return longServerID == that.longServerID;
    }

    public int hashCode() { return hc; }
}
