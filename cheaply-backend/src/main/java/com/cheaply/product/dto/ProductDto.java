package com.cheaply.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * A single product as returned to API clients.
 *
 * <p>Monetary and quantity values are {@link BigDecimal} rather than
 * {@code Double}. The service rounds price-per-unit to two decimal places and
 * then compares those values to decide what is cheapest, and binary floating
 * point is the wrong tool for both halves of that job.
 *
 * <p>{@code pricePerUnit} is null when it could not be computed - because the
 * price did not parse, or because the listing carries no weight or volume. Null
 * means "unknown", and such products are ranked last rather than treated as
 * free.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto implements Serializable {

    private static final long serialVersionUID = 2L;

    private String title;

    /** The price exactly as the store displayed it, for showing back to users. */
    private String price;

    /** The parsed price, or null if the listing had no usable price. */
    private BigDecimal numericPrice;

    /** The weight or volume substring that was matched, e.g. "500 g". */
    private String weight;

    /** That weight converted to the standard unit below, e.g. 0.5. */
    private BigDecimal normalizedWeight;

    /** "kg" or "L". Null when no quantity could be determined. */
    private String unit;

    /** Price per one standard unit; null when unknown. */
    @JsonProperty("price_per_unit")
    private BigDecimal pricePerUnit;

    /**
     * Position within this product's unit group, starting at 1. Null for
     * products with no comparable price-per-unit.
     */
    private Integer rank;

    /** True for the cheapest product in its unit group. */
    private boolean bestValue;

    private String source;
    private String link;

    @JsonProperty("image_url")
    private String imageUrl;
}
