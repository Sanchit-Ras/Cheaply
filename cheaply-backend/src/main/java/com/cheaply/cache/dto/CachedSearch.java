package com.cheaply.cache.dto;

import com.cheaply.product.dto.ProductDto;
import com.cheaply.scraper.dto.StoreStatusDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * What gets stored in Redis for one query: the ranked products and the store
 * outcomes that produced them.
 *
 * <p>The store outcomes are cached alongside the products so a cache hit can
 * still tell a client that, say, Flipkart was down when these results were
 * gathered - otherwise a partial result silently looks complete for the rest of
 * its TTL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CachedSearch {

    @Builder.Default
    private List<ProductDto> products = new ArrayList<>();

    @Builder.Default
    private List<StoreStatusDto> stores = new ArrayList<>();

    public List<ProductDto> productsOrEmpty() {
        return products != null ? products : List.of();
    }

    public List<StoreStatusDto> storesOrEmpty() {
        return stores != null ? stores : List.of();
    }
}
