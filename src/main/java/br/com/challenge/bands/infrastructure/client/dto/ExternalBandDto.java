package br.com.challenge.bands.infrastructure.client.dto;

import java.util.List;
import java.util.UUID;

public record ExternalBandDto(
        UUID id,
        String name,
        String image,
        String genre,
        String biography,
        Long numPlays,
        List<UUID> albums) {}
