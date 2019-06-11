package me.egg82.altfinder.core;

public class PlayerInfoContainer {
    private final PlayerData data;

    private String name = null;

    public PlayerInfoContainer(PlayerData data) { this.data = data; }

    public PlayerData getData() { return data; }

    public PlayerInfoContainer setName(String name) {
        this.name = name;
        return this;
    }

    public String getName() { return name; }
}
