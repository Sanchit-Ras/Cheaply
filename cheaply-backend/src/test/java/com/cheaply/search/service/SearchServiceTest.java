package com.cheaply.search.service;

import com.cheaply.cache.dto.CachedSearch;
import com.cheaply.cache.service.SearchCacheService;
import com.cheaply.exception.InvalidSearchQueryException;
import com.cheaply.history.service.SearchHistoryService;
import com.cheaply.product.dto.ProductDto;
import com.cheaply.product.service.PriceNormalizationService;
import com.cheaply.product.service.RankingService;
import com.cheaply.scraper.client.ScraperClient;
import com.cheaply.scraper.dto.ScrapedProductDto;
import com.cheaply.scraper.dto.ScraperResponse;
import com.cheaply.scraper.dto.StoreStatusDto;
import com.cheaply.search.dto.SearchRequest;
import com.cheaply.search.dto.SearchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private ScraperClient scraperClient;
    @Mock private PriceNormalizationService priceNormalizationService;
    @Mock private RankingService rankingService;
    @Mock private SearchCacheService searchCacheService;
    @Mock private SearchHistoryService searchHistoryService;

    @InjectMocks
    private SearchService searchService;

    private ProductDto product() {
        return ProductDto.builder()
                .title("Tata Salt 1kg").pricePerUnit(new BigDecimal("25.00")).unit("kg").build();
    }

    private StoreStatusDto ok(String name, int count) {
        return StoreStatusDto.builder().name(name).status("ok").count(count).build();
    }

    private StoreStatusDto failed(String name) {
        return StoreStatusDto.builder().name(name).status("failed").error("timeout").build();
    }

    private void stubFreshScrape(ScraperResponse response, List<ProductDto> ranked) {
        when(searchCacheService.get(anyString())).thenReturn(Optional.empty());
        when(scraperClient.scrape(anyString())).thenReturn(response);
        when(priceNormalizationService.normalizePrices(anyList())).thenReturn(ranked);
        when(rankingService.rankProducts(anyList())).thenReturn(ranked);
    }

    @Test
    @DisplayName("a cache hit is served without calling the scraper")
    void servesFromCache() {
        when(searchCacheService.get("salt")).thenReturn(Optional.of(CachedSearch.builder()
                .products(List.of(product()))
                .stores(List.of(ok("Amazon", 1)))
                .build()));

        SearchResponse response = searchService.search(SearchRequest.builder().query("salt").build(), null);

        assertTrue(response.isCached());
        assertEquals(1, response.getTotalResults());
        verifyNoInteractions(scraperClient);
    }

    @Test
    @DisplayName("a cache miss scrapes, normalises, ranks and caches the result")
    void scrapesOnCacheMiss() {
        stubFreshScrape(ScraperResponse.builder()
                .products(List.of(ScrapedProductDto.builder().title("Tata Salt 1kg").price("25").build()))
                .stores(List.of(ok("Amazon", 1), ok("JioMart", 0), ok("Flipkart", 0)))
                .build(), List.of(product()));

        SearchResponse response = searchService.search(SearchRequest.builder().query("salt").build(), null);

        assertFalse(response.isCached());
        assertFalse(response.isPartial());
        assertEquals(1, response.getTotalResults());
        verify(searchCacheService).set(eq("salt"), any(CachedSearch.class), eq(false));
    }

    @Test
    @DisplayName("a failing store makes the response partial and shortens the cache TTL")
    void marksPartialResults() {
        stubFreshScrape(ScraperResponse.builder()
                .products(List.of(ScrapedProductDto.builder().title("Tata Salt 1kg").price("25").build()))
                .stores(List.of(ok("Amazon", 1), failed("Flipkart")))
                .build(), List.of(product()));

        SearchResponse response = searchService.search(SearchRequest.builder().query("salt").build(), null);

        assertTrue(response.isPartial(),
                "a partial comparison presented as complete is worse than no comparison");
        assertEquals(2, response.getStores().size());
        verify(searchCacheService).set(eq("salt"), any(CachedSearch.class), eq(true));
    }

    @Test
    @DisplayName("an empty result is never cached, so an outage is not frozen in for the TTL")
    void doesNotCacheEmptyResults() {
        stubFreshScrape(ScraperResponse.builder()
                .products(List.of())
                .stores(List.of(failed("Amazon"), failed("JioMart"), failed("Flipkart")))
                .build(), List.of());

        SearchResponse response = searchService.search(SearchRequest.builder().query("salt").build(), null);

        assertEquals(0, response.getTotalResults());
        assertTrue(response.isPartial());
        verify(searchCacheService, never()).set(anyString(), any(), anyBoolean());
    }

    @Test
    @DisplayName("an authenticated search is recorded and returns recent queries")
    void recordsHistoryForAuthenticatedUser() {
        when(searchCacheService.get("salt")).thenReturn(Optional.of(CachedSearch.builder()
                .products(List.of(product())).stores(List.of(ok("Amazon", 1))).build()));
        when(searchHistoryService.getRecentSearchQueries("testuser", 5)).thenReturn(List.of("salt", "rice"));

        SearchResponse response = searchService.search(
                SearchRequest.builder().query("salt").build(), "testuser");

        verify(searchHistoryService).saveSearch("testuser", "salt");
        assertEquals(List.of("salt", "rice"), response.getRecentSearches());
    }

    @Test
    @DisplayName("an anonymous search records no history")
    void skipsHistoryForAnonymousUser() {
        when(searchCacheService.get("salt")).thenReturn(Optional.of(CachedSearch.builder()
                .products(List.of(product())).stores(List.of(ok("Amazon", 1))).build()));

        SearchResponse response = searchService.search(SearchRequest.builder().query("salt").build(), null);

        assertNull(response.getRecentSearches());
        verifyNoInteractions(searchHistoryService);
    }

    @Test
    @DisplayName("a history failure downgrades the response instead of failing the search")
    void historyFailureDoesNotFailTheSearch() {
        when(searchCacheService.get("salt")).thenReturn(Optional.of(CachedSearch.builder()
                .products(List.of(product())).stores(List.of(ok("Amazon", 1))).build()));
        org.mockito.Mockito.doThrow(new RuntimeException("database is down"))
                .when(searchHistoryService).saveSearch(anyString(), anyString());

        SearchResponse response = searchService.search(
                SearchRequest.builder().query("salt").build(), "testuser");

        assertEquals(1, response.getTotalResults());
        assertNull(response.getRecentSearches());
    }

    @Test
    @DisplayName("collapses whitespace before looking anything up")
    void normalisesQuery() {
        when(searchCacheService.get("basmati rice")).thenReturn(Optional.of(CachedSearch.builder()
                .products(List.of(product())).stores(List.of(ok("Amazon", 1))).build()));

        SearchResponse response = searchService.search(
                SearchRequest.builder().query("  basmati    rice  ").build(), null);

        assertEquals("basmati rice", response.getQuery());
    }

    @Test
    @DisplayName("rejects a blank query")
    void rejectsBlankQuery() {
        assertThrows(InvalidSearchQueryException.class,
                () -> searchService.search(SearchRequest.builder().query("   ").build(), null));
    }
}
