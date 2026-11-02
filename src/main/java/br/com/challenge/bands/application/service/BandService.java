package br.com.challenge.bands.application.service;

import br.com.challenge.bands.domain.model.Band;
import br.com.challenge.bands.domain.port.BandCatalogPort;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class BandService {

    private static final Comparator<Band> NAME_ORDER = Comparator.comparing(Band::name, String.CASE_INSENSITIVE_ORDER);
    private static final Comparator<Band> NAME_TIE_BREAKER = NAME_ORDER.thenComparing(Band::id);

    private final BandCatalogPort bandCatalog;

    public BandService(BandCatalogPort bandCatalog) {
        this.bandCatalog = bandCatalog;
    }

    public List<Band> findBands(String query, BandSortField sortField, SortDirection direction) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        return bandCatalog.findAll().stream()
                .filter(band -> normalizedQuery.isEmpty()
                        || band.name().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(comparatorFor(sortField, direction))
                .toList();
    }

    public Band findById(UUID id) {
        return bandCatalog.findAll().stream()
                .filter(band -> band.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new BandNotFoundException(id));
    }

    private Comparator<Band> comparatorFor(BandSortField sortField, SortDirection direction) {
        if (sortField == BandSortField.NAME) {
            Comparator<Band> primary = direction == SortDirection.ASC ? NAME_ORDER : NAME_ORDER.reversed();
            return primary.thenComparing(Band::id);
        }

        Comparator<Band> primary = Comparator.comparingLong(Band::numPlays);
        if (direction == SortDirection.DESC) {
            primary = primary.reversed();
        }
        return primary.thenComparing(NAME_TIE_BREAKER);
    }
}
