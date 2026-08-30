package com.cheaply.product.service;

import com.cheaply.product.dto.ProductDto;
import com.cheaply.scraper.dto.ScrapedProductDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the loose strings a scraper produces into comparable numbers.
 *
 * <p>Two things are being recovered here: a price, from text like
 * "&#8377;1,299.00", and a quantity, from text like "500 g" that may only exist
 * inside the product title. Everything is normalised to one of two standard
 * units - kilograms for mass, litres for volume - so that price per unit means
 * the same thing across stores.
 */
@Slf4j
@Service
public class PriceNormalizationService {

    /** Standard unit for anything measured by mass. */
    public static final String UNIT_KG = "kg";

    /** Standard unit for anything measured by volume. */
    public static final String UNIT_LITRE = "L";

    /**
     * Longer unit names come first in the alternation so that "litre" is not
     * matched as a bare "l" with a trailing "itre".
     */
    private static final Pattern WEIGHT_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(kg|litre|liter|ml|g|l)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /** Anything that is not a digit or a decimal point. */
    private static final Pattern NON_NUMERIC = Pattern.compile("[^\\d.]");

    private static final int PRICE_SCALE = 2;
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    public List<ProductDto> normalizePrices(List<ScrapedProductDto> scrapedProducts) {
        if (scrapedProducts == null || scrapedProducts.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProductDto> normalized = new ArrayList<>(scrapedProducts.size());
        for (ScrapedProductDto scraped : scrapedProducts) {
            if (scraped != null) {
                normalized.add(normalizeProduct(scraped));
            }
        }
        return normalized;
    }

    public ProductDto normalizeProduct(ScrapedProductDto scraped) {
        BigDecimal numericPrice = parsePrice(scraped.getPrice());
        Quantity quantity = parseQuantity(scraped.getWeight(), scraped.getTitle());

        BigDecimal pricePerUnit = null;
        if (numericPrice != null && quantity != null
                && quantity.amount().compareTo(BigDecimal.ZERO) > 0) {
            pricePerUnit = numericPrice.divide(quantity.amount(), PRICE_SCALE, RoundingMode.HALF_UP);
        }

        return ProductDto.builder()
                .title(trimOrEmpty(scraped.getTitle()))
                .price(scraped.getPrice() != null ? scraped.getPrice().trim() : "N/A")
                .numericPrice(numericPrice)
                .weight(quantity != null ? quantity.rawMatch() : trimOrNull(scraped.getWeight()))
                .normalizedWeight(quantity != null ? quantity.amount() : null)
                .unit(quantity != null ? quantity.unit() : null)
                .pricePerUnit(pricePerUnit)
                .source(trimOrEmpty(scraped.getSource()))
                .link(trimOrEmpty(scraped.getLink()))
                .imageUrl(trimOrEmpty(scraped.getImageUrl()))
                .build();
    }

    /**
     * Extracts a price from display text.
     *
     * <p>Returns null rather than zero when nothing usable is present. Zero
     * would sort to the top of a cheapest-first ranking, which is exactly the
     * wrong answer for a listing whose price we failed to read.
     */
    BigDecimal parsePrice(String priceRaw) {
        if (priceRaw == null) {
            return null;
        }
        String trimmed = priceRaw.trim();
        if (trimmed.isEmpty() || "N/A".equalsIgnoreCase(trimmed) || "NA".equalsIgnoreCase(trimmed)) {
            return null;
        }

        String cleaned = NON_NUMERIC.matcher(trimmed.replace(",", "")).replaceAll("");
        // Amazon renders "1,299." for the rupee portion of a price, so a
        // trailing separator is normal rather than a parse failure.
        cleaned = cleaned.replaceAll("\\.+$", "");
        if (cleaned.isEmpty()) {
            return null;
        }

        try {
            BigDecimal value = new BigDecimal(cleaned);
            return value.compareTo(BigDecimal.ZERO) > 0
                    ? value.setScale(PRICE_SCALE, RoundingMode.HALF_UP)
                    : null;
        } catch (NumberFormatException e) {
            // Reached when the text contained several decimal points, e.g. a
            // stray "1.299.00" from a locale-formatted listing.
            log.debug("Could not parse a price from '{}'", priceRaw);
            return null;
        }
    }

    /**
     * Finds a quantity, preferring an explicit weight field and falling back to
     * the product title. Returns null when there is nothing to find - a plain
     * count of items ("pack of 6") is not a quantity we can normalise.
     */
    Quantity parseQuantity(String rawWeight, String title) {
        Quantity fromWeightField = matchQuantity(rawWeight);
        if (fromWeightField != null) {
            return fromWeightField;
        }
        return matchQuantity(title);
    }

    private Quantity matchQuantity(String text) {
        if (text == null || text.trim().isEmpty() || "N/A".equalsIgnoreCase(text.trim())) {
            return null;
        }

        Matcher matcher = WEIGHT_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        try {
            BigDecimal value = new BigDecimal(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();
            String rawMatch = matcher.group(0).trim();

            return switch (unit) {
                case "kg" -> new Quantity(rawMatch, value, UNIT_KG);
                case "g" -> new Quantity(rawMatch, divideByThousand(value), UNIT_KG);
                case "ml" -> new Quantity(rawMatch, divideByThousand(value), UNIT_LITRE);
                case "l", "litre", "liter" -> new Quantity(rawMatch, value, UNIT_LITRE);
                default -> null;
            };
        } catch (NumberFormatException e) {
            log.debug("Could not parse a quantity from '{}'", text);
            return null;
        }
    }

    private BigDecimal divideByThousand(BigDecimal value) {
        // Six decimal places keeps a 1 g listing meaningful without pretending
        // to a precision the source data does not have.
        return value.divide(THOUSAND, 6, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private String trimOrEmpty(String value) {
        return value != null ? value.trim() : "";
    }

    private String trimOrNull(String value) {
        return value != null && !value.trim().isEmpty() ? value.trim() : null;
    }

    /**
     * A quantity resolved to one of the two standard units.
     *
     * @param rawMatch the text that was matched, for display
     * @param amount   the quantity expressed in {@code unit}
     * @param unit     {@link #UNIT_KG} or {@link #UNIT_LITRE}
     */
    record Quantity(String rawMatch, BigDecimal amount, String unit) {
    }
}
