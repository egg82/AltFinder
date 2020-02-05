package me.egg82.altfinder.services;

import java.util.UUID;
import me.egg82.altfinder.messaging.Messaging;

public interface MessagingHandler {
    void ipCallback(UUID messageID, String ip, long longIPID, Messaging callingMessaging);
    void playerCallback(UUID messageID, UUID playerID, long longPlayerID, Messaging callingMessaging);
    void postCallback(UUID messageID, long id, long longIPID, String ip, long longPlayerID, UUID playerID, long longServerID, UUID serverID, String serverName, long count, long created, long updated, Messaging callingMessaging);
}
