package br.com.challenge.bands.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Band(
        UUID id,
        String name,
        String image,
        String genre,
        String biography,
        long numPlays,
        List<UUID> albumIds) {

    public Band {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (numPlays < 0) {
            throw new IllegalArgumentException("numPlays must not be negative");
        }
        albumIds = List.copyOf(Objects.requireNonNull(albumIds, "albumIds must not be null"));
    }
}
