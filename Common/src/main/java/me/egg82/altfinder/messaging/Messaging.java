package me.egg82.altfinder.messaging;

import java.util.UUID;

public interface Messaging {
    void close();
    boolean isClosed();

    void sendServer(UUID messageID, long longServerID, UUID serverID, String name) throws MessagingException;
    void sendIP(UUID messageID, long longIPID, String ip) throws MessagingException;
    void sendPlayer(UUID messageID, long longPlayerID, UUID playerID) throws MessagingException;
    void sendPost(UUID messageID, long id, long longIPID, String ip, long longPlayerID, UUID playerID, long longServerID, UUID serverID, String serverName, long count, long created, long updated) throws MessagingException;
}
