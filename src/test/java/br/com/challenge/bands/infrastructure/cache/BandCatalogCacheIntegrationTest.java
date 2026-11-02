package br.com.challenge.bands.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.challenge.bands.application.service.BandService;
import br.com.challenge.bands.application.service.BandSortField;
import br.com.challenge.bands.application.service.SortDirection;
import br.com.challenge.bands.domain.model.Band;
import br.com.challenge.bands.domain.port.BandCatalogPort;
import br.com.challenge.bands.infrastructure.client.BandApiClient;
import br.com.challenge.bands.infrastructure.client.exception.ExternalServiceException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Ticker;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(BandCatalogCacheIntegrationTest.CacheTestConfiguration.class)
class BandCatalogCacheIntegrationTest {

    private static final UUID BAND_ID = UUID.fromString("bc710bcf-8815-42cf-bad2-3f1d12246aeb");
    private static final Band BAND = new Band(BAND_ID, "Nickelback", null, "rock", null, 17605491, List.of());
    private static final List<Band> CATALOG = List.of(BAND);
    private static final List<Band> REFRESHED_CATALOG = List.of(
            new Band(UUID.fromString("e2c00c56-8365-4160-9f40-a64682917633"), "Goo Goo Dolls", null, "rock", null, 1, List.of()));

    @Autowired
    private BandService bandService;

    @Autowired
    private BandCatalogPort bandCatalogPort;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ManualTicker ticker;

    @MockitoBean
    private BandApiClient bandApiClient;

    @BeforeEach
    void clearCacheAndResetTicker() {
        cacheManager.getCache(CacheConfiguration.BANDS_CATALOG_CACHE).clear();
        ticker.reset();
    }

    @Test
    void shouldFetchOnFirstCallAndUseCacheOnSecondCall() {
        when(bandApiClient.findAll()).thenReturn(CATALOG);

        assertEquals(CATALOG, bandCatalogPort.findAll());
        assertEquals(CATALOG, bandCatalogPort.findAll());

        verify(bandApiClient, times(1)).findAll();
        assertTrue(AopUtils.isAopProxy(bandCatalogPort));
    }

    @Test
    void shouldReuseCachedCatalogForSearch() {
        when(bandApiClient.findAll()).thenReturn(CATALOG);

        bandService.findBands(null, BandSortField.NAME, SortDirection.ASC);
        assertEquals(List.of(BAND), bandService.findBands("nick", BandSortField.NAME, SortDirection.ASC));

        verify(bandApiClient, times(1)).findAll();
    }

    @Test
    void shouldReuseCachedCatalogForSorting() {
        when(bandApiClient.findAll()).thenReturn(CATALOG);

        bandService.findBands(null, BandSortField.NAME, SortDirection.ASC);
        assertEquals(List.of(BAND), bandService.findBands(null, BandSortField.NUM_PLAYS, SortDirection.DESC));

        verify(bandApiClient, times(1)).findAll();
    }

    @Test
    void shouldReuseCachedCatalogForLookupById() {
        when(bandApiClient.findAll()).thenReturn(CATALOG);

        bandService.findById(BAND_ID);
        assertEquals(BAND, bandService.findById(BAND_ID));

        verify(bandApiClient, times(1)).findAll();
    }

    @Test
    void shouldExpireCatalogAfterConfiguredTtlAndLoadItAgain() {
        when(bandApiClient.findAll()).thenReturn(CATALOG).thenReturn(REFRESHED_CATALOG);

        assertEquals(CATALOG, bandCatalogPort.findAll());
        ticker.advance(Duration.ofMinutes(5).plusNanos(1));
        assertEquals(REFRESHED_CATALOG, bandCatalogPort.findAll());

        verify(bandApiClient, times(2)).findAll();
    }

    @Test
    void shouldNotCacheExceptionsFromExternalAdapter() {
        ExternalServiceException failure = new ExternalServiceException("upstream failure", new ConnectException());
        when(bandApiClient.findAll()).thenThrow(failure).thenReturn(CATALOG);

        assertThrows(ExternalServiceException.class, bandCatalogPort::findAll);
        assertEquals(CATALOG, bandCatalogPort.findAll());
        assertEquals(CATALOG, bandCatalogPort.findAll());

        verify(bandApiClient, times(2)).findAll();
    }

    @Test
    void shouldKeepCatalogAndServiceResultsImmutable() {
        when(bandApiClient.findAll()).thenReturn(CATALOG);

        List<Band> cachedCatalog = bandCatalogPort.findAll();
        List<Band> serviceResult = bandService.findBands(null, BandSortField.NAME, SortDirection.ASC);

        assertThrows(UnsupportedOperationException.class, () -> cachedCatalog.add(BAND));
        assertThrows(UnsupportedOperationException.class, () -> serviceResult.add(BAND));
        verify(bandApiClient, times(1)).findAll();
    }

    @Test
    void shouldApplyConfiguredCacheNameTtlAndMaximumSize() {
        org.springframework.cache.Cache springCache = cacheManager.getCache(CacheConfiguration.BANDS_CATALOG_CACHE);
        Object nativeCache = ((CaffeineCache) springCache).getNativeCache();
        if (nativeCache instanceof Cache<?, ?> caffeineCache) {
            assertEquals(
                    Duration.ofMinutes(5).toNanos(),
                    caffeineCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.NANOSECONDS));
            assertEquals(100, caffeineCache.policy().eviction().orElseThrow().getMaximum());
        } else {
            throw new AssertionError("Expected Caffeine native cache");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CacheTestConfiguration {

        @Bean
        ManualTicker manualTicker() {
            return new ManualTicker();
        }
    }

    static class ManualTicker implements Ticker {

        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }

        void reset() {
            nanos.set(0);
        }
    }
}
