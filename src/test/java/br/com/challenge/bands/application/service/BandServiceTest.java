package br.com.challenge.bands.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.challenge.bands.domain.model.Band;
import br.com.challenge.bands.domain.port.BandCatalogPort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BandServiceTest {

    private static final Band ALPHA = band("00000000-0000-0000-0000-000000000001", "alpha", 100);
    private static final Band ALPHA_UPPER = band("00000000-0000-0000-0000-000000000002", "Alpha", 100);
    private static final Band BRAVO = band("00000000-0000-0000-0000-000000000003", "Bravo", 300);
    private static final Band DELTA = band("00000000-0000-0000-0000-000000000004", "delta", 100);
    private static final List<Band> BANDS = List.of(BRAVO, DELTA, ALPHA, ALPHA_UPPER);

    @Mock
    private BandCatalogPort bandCatalog;

    private BandService service;

    @BeforeEach
    void setUp() {
        service = new BandService(bandCatalog);
        when(bandCatalog.findAll()).thenReturn(BANDS);
    }

    @Test
    void shouldReturnCompleteListInRequestedDefaultOrder() {
        assertEquals(List.of("alpha", "Alpha", "Bravo", "delta"), names(service.findBands(null, BandSortField.NAME, SortDirection.ASC)));
        verify(bandCatalog).findAll();
    }

    @Test
    void shouldReturnAnEmptyListWhenCatalogIsEmpty() {
        when(bandCatalog.findAll()).thenReturn(List.of());

        assertEquals(List.of(), service.findBands(null, BandSortField.NAME, SortDirection.ASC));
    }

    @Test
    void shouldSearchByPartialName() {
        assertEquals(List.of("Bravo"), names(service.findBands("rav", BandSortField.NAME, SortDirection.ASC)));
    }

    @Test
    void shouldSearchWithoutCaseSensitivity() {
        assertEquals(List.of("Bravo"), names(service.findBands("BRAVO", BandSortField.NAME, SortDirection.ASC)));
    }

    @Test
    void shouldTrimOuterSpacesFromSearchTerm() {
        assertEquals(List.of("Bravo"), names(service.findBands("  bravo  ", BandSortField.NAME, SortDirection.ASC)));
    }

    @Test
    void shouldReturnEmptyWhenSearchHasNoMatches() {
        assertEquals(List.of(), service.findBands("missing", BandSortField.NAME, SortDirection.ASC));
    }

    @Test
    void shouldSortByNameAscending() {
        assertEquals(List.of("alpha", "Alpha", "Bravo", "delta"), names(service.findBands(null, BandSortField.NAME, SortDirection.ASC)));
    }

    @Test
    void shouldSortByNameDescending() {
        assertEquals(List.of("delta", "Bravo", "alpha", "Alpha"), names(service.findBands(null, BandSortField.NAME, SortDirection.DESC)));
    }

    @Test
    void shouldSortByPlaysAscending() {
        assertEquals(List.of("alpha", "Alpha", "delta", "Bravo"), names(service.findBands(null, BandSortField.NUM_PLAYS, SortDirection.ASC)));
    }

    @Test
    void shouldSortByPlaysDescending() {
        assertEquals(List.of("Bravo", "alpha", "Alpha", "delta"), names(service.findBands(null, BandSortField.NUM_PLAYS, SortDirection.DESC)));
    }

    @Test
    void shouldSortTiesByNameThenIdDeterministically() {
        assertEquals(
                List.of(ALPHA, ALPHA_UPPER, DELTA, BRAVO),
                service.findBands("a", BandSortField.NUM_PLAYS, SortDirection.ASC));
    }

    @Test
    void shouldFindBandById() {
        assertEquals(BRAVO, service.findById(BRAVO.id()));
    }

    @Test
    void shouldThrowWhenBandIdDoesNotExist() {
        UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000099");

        assertThrows(BandNotFoundException.class, () -> service.findById(missingId));
    }

    @Test
    void shouldCombineSearchAndSorting() {
        assertEquals(
                List.of("Bravo", "alpha", "Alpha", "delta"),
                names(service.findBands("a", BandSortField.NUM_PLAYS, SortDirection.DESC)));
    }

    private static List<String> names(List<Band> bands) {
        return bands.stream().map(Band::name).toList();
    }

    private static Band band(String id, String name, long numPlays) {
        return new Band(UUID.fromString(id), name, null, null, null, numPlays, List.of());
    }
}
