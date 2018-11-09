package me.egg82.altfinder.core;

import java.util.UUID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class PlayerData {
    private final UUID uuid;
    private final String ip;
    private final long count;
    private final String server;
    private final long created;
    private final long updated;

    private final int hc;

    public PlayerData(UUID uuid, String ip, long count, String server, long created, long updated) {
        this.uuid = uuid;
        this.ip = ip;
        this.count = count;
        this.server = server;
        this.created = created;
        this.updated = updated;

        this.hc = new HashCodeBuilder(16619, 58511).append(uuid).append(ip).toHashCode();
    }

    public UUID getUUID() { return uuid; }

    public String getIP() { return ip; }

    public long getCount() { return count; }

    public String getServer() { return server; }

    public long getCreated() { return created; }

    public long getUpdated() { return updated; }

    public boolean equals(Object obj) {
        if (obj == null || !obj.getClass().equals(getClass())) {
            return false;
        }
        if (obj == this) {
            return true;
        }

        PlayerData o = (PlayerData) obj;
        return new EqualsBuilder().append(uuid, o.uuid).append(ip, o.ip).isEquals();
    }

    public int hashCode() { return hc; }
}
