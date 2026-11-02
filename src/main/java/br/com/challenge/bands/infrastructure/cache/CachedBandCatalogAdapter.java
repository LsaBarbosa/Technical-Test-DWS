package br.com.challenge.bands.infrastructure.cache;

import br.com.challenge.bands.domain.model.Band;
import br.com.challenge.bands.domain.port.BandCatalogPort;
import br.com.challenge.bands.infrastructure.client.BandApiClient;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class CachedBandCatalogAdapter implements BandCatalogPort {

    private final BandApiClient bandApiClient;

    public CachedBandCatalogAdapter(BandApiClient bandApiClient) {
        this.bandApiClient = bandApiClient;
    }

    @Override
    @Cacheable(cacheNames = CacheConfiguration.BANDS_CATALOG_CACHE)
    public List<Band> findAll() {
        return List.copyOf(bandApiClient.findAll());
    }
}
