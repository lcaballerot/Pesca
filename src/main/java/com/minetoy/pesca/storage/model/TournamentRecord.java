package com.minetoy.pesca.storage.model;

import java.util.UUID;

/** A finished tournament as stored in the database. */
public record TournamentRecord(
        int id,
        long startedAt,
        long endsAt,
        UUID winnerUuid,
        String winnerName,
        int winnerPoints
) {
}
