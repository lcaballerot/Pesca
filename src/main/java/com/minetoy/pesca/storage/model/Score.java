package com.minetoy.pesca.storage.model;

import java.util.UUID;

/**
 * A player's running total inside one tournament. Mutable on purpose — the live
 * tournament keeps these in memory and flushes them to SQLite periodically.
 */
public final class Score {

    public final UUID uuid;
    public String name;

    public int points;
    public int catches;
    public double bestKg;
    public String bestFish;

    public Score(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
}
