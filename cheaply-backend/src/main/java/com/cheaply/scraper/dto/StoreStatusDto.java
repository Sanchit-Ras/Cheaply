package com.cheaply.scraper.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Per-store outcome of a scrape.
 *
 * <p>This exists because a silently failing store used to be indistinguishable
 * from a store that genuinely had no matching products: both produced an empty
 * list and a 200. When a site changes its markup, that difference is the only
 * signal anyone gets.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StoreStatusDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** "Amazon", "JioMart", "Flipkart". */
    private String name;

    /** One of: ok, empty, failed. */
    private String status;

    /** Number of products this store contributed. */
    private int count;

    /** Short failure reason when status is "failed"; null otherwise. */
    private String error;

    public boolean isFailed() {
        return "failed".equalsIgnoreCase(status);
    }
}
