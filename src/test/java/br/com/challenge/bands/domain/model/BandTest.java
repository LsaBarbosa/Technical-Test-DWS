package br.com.challenge.bands.domain.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BandTest {

    @Test
    void shouldRejectNameContainingOnlyWhitespace() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Band(UUID.randomUUID(), "   \t", null, null, null, 0, List.of()));
    }
}
