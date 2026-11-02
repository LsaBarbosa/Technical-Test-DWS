package br.com.challenge.bands.web.controller;

import br.com.challenge.bands.application.service.BandService;
import br.com.challenge.bands.application.service.BandSortField;
import br.com.challenge.bands.application.service.SortDirection;
import br.com.challenge.bands.domain.model.Band;
import br.com.challenge.bands.web.dto.BandDetailResponse;
import br.com.challenge.bands.web.dto.BandSummaryResponse;
import br.com.challenge.bands.web.exception.InvalidRequestParameterException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bands")
public class BandController {

    private final BandService bandService;

    public BandController(BandService bandService) {
        this.bandService = bandService;
    }

    @GetMapping
    public List<BandSummaryResponse> findBands(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction) {
        return bandService.findBands(query, parseSortField(sortBy), parseDirection(direction)).stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public BandDetailResponse findBandById(@PathVariable UUID id) {
        return toDetailResponse(bandService.findById(id));
    }

    private BandSortField parseSortField(String value) {
        return switch (value) {
            case "name" -> BandSortField.NAME;
            case "numPlays" -> BandSortField.NUM_PLAYS;
            default -> throw new InvalidRequestParameterException("sortBy must be 'name' or 'numPlays'");
        };
    }

    private SortDirection parseDirection(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "asc" -> SortDirection.ASC;
            case "desc" -> SortDirection.DESC;
            default -> throw new InvalidRequestParameterException("direction must be 'asc' or 'desc'");
        };
    }

    private BandSummaryResponse toSummaryResponse(Band band) {
        return new BandSummaryResponse(band.id(), band.name(), band.image(), band.numPlays());
    }

    private BandDetailResponse toDetailResponse(Band band) {
        return new BandDetailResponse(
                band.id(), band.name(), band.image(), band.genre(), band.biography(), band.numPlays(), band.albumIds());
    }
}
