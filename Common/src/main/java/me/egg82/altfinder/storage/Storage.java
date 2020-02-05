package me.egg82.altfinder.storage;

import java.util.Set;
import java.util.UUID;
import me.egg82.altfinder.core.*;

public interface Storage {
    void close();
    boolean isClosed();

    Set<AltResult> getQueue() throws StorageException;
    Set<AltResult> getByIP(String ip, int days) throws StorageException;
    Set<AltResult> getByPlayer(UUID playerID, int days) throws StorageException;
    PostAltResult post(UUID playerID, String ip) throws StorageException;

    void setServerRaw(long longServerID, UUID serverID, String name) throws StorageException;
    void setIPRaw(long longIPID, String ip) throws StorageException;
    void setPlayerRaw(long longPlayerID, UUID playerID) throws StorageException;
    void postRaw(long id, long longIPID, long longPlayerID, long longServerID, long count, long created, long updated) throws StorageException;

    void setServerName(String name) throws StorageException;

    long getLongPlayerID(UUID playerID);
    long getLongIPID(String ip);

    Set<ServerResult> dumpServers() throws StorageException;
    void loadServers(Set<ServerResult> servers) throws StorageException;

    Set<IPResult> dumpIPs(long begin, int size) throws StorageException;
    void loadIPs(Set<IPResult> ips, boolean truncate) throws StorageException;

    Set<PlayerResult> dumpPlayers(long begin, int size) throws StorageException;
    void loadPlayers(Set<PlayerResult> players, boolean truncate) throws StorageException;

    Set<RawAltResult> dumpAltValues(long begin, int size) throws StorageException;
    void loadAltValues(Set<RawAltResult> values, boolean truncate) throws StorageException;
}
