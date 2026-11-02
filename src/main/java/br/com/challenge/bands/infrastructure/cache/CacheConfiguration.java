package br.com.challenge.bands.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties(BandCacheProperties.class)
public class CacheConfiguration {

    public static final String BANDS_CATALOG_CACHE = "bandsCatalog";

    @Bean
    CacheManager cacheManager(BandCacheProperties properties, ObjectProvider<Ticker> tickerProvider) {
        Caffeine<Object, Object> caffeine = Caffeine.newBuilder()
                .expireAfterWrite(properties.ttl())
                .maximumSize(properties.maximumSize());
        tickerProvider.ifAvailable(caffeine::ticker);

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeine);
        cacheManager.setCacheNames(List.of(BANDS_CATALOG_CACHE));
        return cacheManager;
    }
}
