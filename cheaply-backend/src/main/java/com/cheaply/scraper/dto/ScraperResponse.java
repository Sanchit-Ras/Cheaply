package com.cheaply.scraper.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * The envelope returned by the Python scraper service.
 *
 * <p>The service used to return a bare JSON array of products. It now returns
 * this object so the backend can tell the difference between "all three stores
 * reported no matches" and "two stores blew up".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScraperResponse {

    private String query;

    @Builder.Default
    private List<ScrapedProductDto> products = new ArrayList<>();

    @Builder.Default
    private List<StoreStatusDto> stores = new ArrayList<>();

    @JsonAlias({"duration_ms", "durationMs"})
    private Long durationMs;

    public List<ScrapedProductDto> productsOrEmpty() {
        return products != null ? products : List.of();
    }

    public List<StoreStatusDto> storesOrEmpty() {
        return stores != null ? stores : List.of();
    }

    /** True when at least one store failed outright. */
    public boolean isPartial() {
        return storesOrEmpty().stream().anyMatch(StoreStatusDto::isFailed);
    }
}
