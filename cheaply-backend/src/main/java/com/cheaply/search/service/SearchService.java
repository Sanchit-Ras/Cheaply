package com.cheaply.search.service;

import com.cheaply.cache.dto.CachedSearch;
import com.cheaply.cache.service.SearchCacheService;
import com.cheaply.exception.InvalidSearchQueryException;
import com.cheaply.history.service.SearchHistoryService;
import com.cheaply.product.dto.ProductDto;
import com.cheaply.product.service.PriceNormalizationService;
import com.cheaply.product.service.RankingService;
import com.cheaply.scraper.client.ScraperClient;
import com.cheaply.scraper.dto.ScraperResponse;
import com.cheaply.scraper.dto.StoreStatusDto;
import com.cheaply.search.dto.SearchRequest;
import com.cheaply.search.dto.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates one product search: cache lookup, scrape, normalise, rank, and
 * record it against the user's history.
 *
 * <p>The ordering matters. The cache is consulted first because a scrape is by
 * far the most expensive thing this system does, and history is written last
 * because a failure to record a search should never fail the search itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private static final int MAX_QUERY_LENGTH = 120;
    private static final int RECENT_SEARCHES_IN_RESPONSE = 5;

    private final ScraperClient scraperClient;
    private final PriceNormalizationService priceNormalizationService;
    private final RankingService rankingService;
    private final SearchCacheService searchCacheService;
    private final SearchHistoryService searchHistoryService;

    public SearchResponse search(SearchRequest request, String currentUsername) {
        String query = sanitizeQuery(request.getQuery());
        if (query.isEmpty()) {
            throw new InvalidSearchQueryException("Search query cannot be empty");
        }

        log.info("Search for '{}' (user: {})", query, currentUsername != null ? currentUsername : "anonymous");

        Optional<CachedSearch> cached = searchCacheService.get(query);
        CachedSearch result;
        boolean servedFromCache = cached.isPresent();

        if (servedFromCache) {
            result = cached.get();
            log.info("Served {} products for '{}' from cache", result.productsOrEmpty().size(), query);
        } else {
            result = performScrape(query);
        }

        List<ProductDto> products = result.productsOrEmpty();
        List<StoreStatusDto> stores = result.storesOrEmpty();

        return SearchResponse.builder()
                .query(query)
                .totalResults(products.size())
                .cached(servedFromCache)
                .partial(stores.stream().anyMatch(StoreStatusDto::isFailed))
                .stores(stores.isEmpty() ? null : stores)
                .products(products)
                .recentSearches(recordHistory(currentUsername, query))
                .build();
    }

    private CachedSearch performScrape(String query) {
        log.info("Cache miss for '{}' - calling the scraper service", query);

        ScraperResponse scraped = scraperClient.scrape(query);

        List<ProductDto> ranked = rankingService.rankProducts(
                priceNormalizationService.normalizePrices(scraped.productsOrEmpty()));

        CachedSearch result = CachedSearch.builder()
                .products(ranked)
                .stores(scraped.storesOrEmpty())
                .build();

        // Empty results are deliberately not cached: an empty list is as likely
        // to mean "every store failed" as "nothing matched", and caching it
        // would hide a real outage for the length of the TTL.
        if (!ranked.isEmpty()) {
            searchCacheService.set(query, result, scraped.isPartial());
        } else {
            log.warn("No products found for '{}'; store outcomes: {}", query, scraped.storesOrEmpty());
        }

        return result;
    }

    /**
     * Records the search and returns the user's recent queries.
     *
     * <p>Wrapped so that a database problem downgrades the response rather than
     * failing it - the products have already been gathered at this point and
     * are what the caller actually asked for.
     */
    private List<String> recordHistory(String username, String query) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            searchHistoryService.saveSearch(username, query);
            List<String> recent = searchHistoryService.getRecentSearchQueries(username, RECENT_SEARCHES_IN_RESPONSE);
            return recent.isEmpty() ? null : recent;
        } catch (Exception e) {
            log.warn("Could not record search history for '{}': {}", username, e.getMessage());
            return null;
        }
    }

    /**
     * Collapses whitespace and enforces the length limit. Bean validation
     * already rejects oversized input at the controller, so this is a
     * belt-and-braces guard for any non-HTTP caller.
     */
    private String sanitizeQuery(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > MAX_QUERY_LENGTH) {
            cleaned = cleaned.substring(0, MAX_QUERY_LENGTH).trim();
        }
        return cleaned;
    }

}
