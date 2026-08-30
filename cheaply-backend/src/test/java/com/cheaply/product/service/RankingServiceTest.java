package com.cheaply.product.service;

import com.cheaply.product.dto.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingServiceTest {

    private RankingService service;

    @BeforeEach
    void setUp() {
        service = new RankingService();
    }

    private ProductDto product(String title, String pricePerUnit, String unit) {
        return ProductDto.builder()
                .title(title)
                .pricePerUnit(pricePerUnit != null ? new BigDecimal(pricePerUnit) : null)
                .unit(unit)
                .build();
    }

    @Test
    @DisplayName("orders products cheapest-first within a unit")
    void ranksAscendingWithinUnit() {
        List<ProductDto> ranked = service.rankProducts(List.of(
                product("Store A", "100", "kg"),
                product("Store B", "45.50", "kg"),
                product("Store C", "70", "kg")));

        assertEquals(List.of("Store B", "Store C", "Store A"),
                ranked.stream().map(ProductDto::getTitle).toList());
        assertEquals(List.of(1, 2, 3), ranked.stream().map(ProductDto::getRank).toList());
    }

    @Test
    @DisplayName("marks only the cheapest product in a unit as best value")
    void flagsBestValue() {
        List<ProductDto> ranked = service.rankProducts(List.of(
                product("Expensive", "100", "kg"),
                product("Cheapest", "45", "kg")));

        assertTrue(ranked.get(0).isBestValue());
        assertFalse(ranked.get(1).isBestValue());
    }

    @Test
    @DisplayName("never ranks a per-litre price against a per-kilogram price")
    void doesNotCompareAcrossUnits() {
        // Rice at 60/kg is not "cheaper" than oil at 150/L in any sense a
        // shopper cares about, so the two must not share a ranking.
        List<ProductDto> ranked = service.rankProducts(List.of(
                product("Oil", "150", "L"),
                product("Rice", "60", "kg"),
                product("Premium Oil", "220", "L"),
                product("Premium Rice", "90", "kg")));

        assertEquals(List.of("Rice", "Premium Rice", "Oil", "Premium Oil"),
                ranked.stream().map(ProductDto::getTitle).toList());

        // Each unit gets its own rank sequence and its own best-value winner.
        assertEquals(1, ranked.get(0).getRank());
        assertEquals(1, ranked.get(2).getRank());
        assertEquals(2, ranked.stream().filter(ProductDto::isBestValue).count());
    }

    @Test
    @DisplayName("places products with no comparable price at the end without dropping them")
    void placesUncomparableProductsLast() {
        List<ProductDto> ranked = service.rankProducts(List.of(
                product("No price", null, "kg"),
                product("Priced", "80", "kg"),
                product("No unit", "50", null)));

        assertEquals(3, ranked.size(), "a listing we cannot rank is still a listing users want to see");
        assertEquals("Priced", ranked.get(0).getTitle());
        assertNull(ranked.get(1).getRank());
        assertNull(ranked.get(2).getRank());
        assertFalse(ranked.get(1).isBestValue());
    }

    @Test
    @DisplayName("handles empty and null input")
    void handlesEmptyInput() {
        assertTrue(service.rankProducts(null).isEmpty());
        assertTrue(service.rankProducts(List.of()).isEmpty());
    }

    @Test
    @DisplayName("puts an unexpected unit after the known ones rather than losing it")
    void toleratesUnknownUnits() {
        List<ProductDto> ranked = service.rankProducts(List.of(
                product("Odd", "10", "piece"),
                product("Rice", "60", "kg")));

        assertEquals(2, ranked.size());
        assertEquals("Rice", ranked.get(0).getTitle());
        assertEquals("Odd", ranked.get(1).getTitle());
    }
}
