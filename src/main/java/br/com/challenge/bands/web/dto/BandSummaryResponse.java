package br.com.challenge.bands.web.dto;

import java.util.UUID;

public record BandSummaryResponse(UUID id, String name, String image, long numPlays) {}
