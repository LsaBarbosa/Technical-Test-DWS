package br.com.challenge.bands.web.dto;

import java.util.List;
import java.util.UUID;

public record BandDetailResponse(
        UUID id,
        String name,
        String image,
        String genre,
        String biography,
        long numPlays,
        List<UUID> albums) {}
