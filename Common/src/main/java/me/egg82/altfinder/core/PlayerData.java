package me.egg82.altfinder.core;

import java.util.Objects;
import java.util.UUID;

public class PlayerData {
    private final UUID uuid;
    private final String ip;
    private final long count;
    private final String server;
    private final long created;
    private final long updated;

    private final int computedHash;

    public PlayerData(UUID uuid, String ip, long count, String server, long created, long updated) {
        this.uuid = uuid;
        this.ip = ip;
        this.count = count;
        this.server = server;
        this.created = created;
        this.updated = updated;

        computedHash = Objects.hash(uuid, ip);
    }

    public UUID getUUID() { return uuid; }

    public String getIP() { return ip; }

    public long getCount() { return count; }

    public String getServer() { return server; }

    public long getCreated() { return created; }

    public long getUpdated() { return updated; }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerData that = (PlayerData) o;
        return uuid.equals(that.uuid) &&
                ip.equals(that.ip);
    }

    public int hashCode() {
        return computedHash;
    }
}
