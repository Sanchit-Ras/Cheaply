package com.cheaply.search.dto;

import com.cheaply.product.dto.ProductDto;
import com.cheaply.scraper.dto.StoreStatusDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponse {

    private String query;

    private int totalResults;

    /** True when these results came from Redis rather than a fresh scrape. */
    private boolean cached;

    /**
     * True when at least one store failed while gathering these results, so the
     * comparison is incomplete. Clients should say so rather than presenting a
     * partial list as the whole market.
     */
    private boolean partial;

    /**
     * Per-store outcome. Present on every response, including cached ones,
     * where it describes the scrape that originally produced the entry.
     */
    private List<StoreStatusDto> stores;

    /**
     * Products ordered cheapest-first within each unit group. See
     * {@link com.cheaply.product.service.RankingService} for what that means.
     */
    private List<ProductDto> products;

    /** The authenticated user's recent queries; null for anonymous callers. */
    private List<String> recentSearches;
}
