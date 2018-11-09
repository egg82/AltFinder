package me.egg82.altfinder.core;

public class SQLFetchResult {
    private final PlayerData[] data;
    private final String[] removedKeys;

    public SQLFetchResult(PlayerData[] data, String[] removedKeys) {
        this.data = data;
        this.removedKeys = removedKeys;
    }

    public PlayerData[] getData() { return data; }

    public String[] getRemovedKeys() { return removedKeys; }
}
