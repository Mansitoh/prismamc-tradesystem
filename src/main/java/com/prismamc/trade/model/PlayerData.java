package com.prismamc.trade.model;

import java.util.UUID;

public class PlayerData {
    private UUID uuid;
    private String playerName;
    private String language;

    public PlayerData(UUID uuid, String playerName, String language) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.language = language;
    }

    // Getters and Setters
    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}