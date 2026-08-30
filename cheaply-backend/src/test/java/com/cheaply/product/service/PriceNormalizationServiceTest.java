package com.cheaply.product.service;

import com.cheaply.product.dto.ProductDto;
import com.cheaply.scraper.dto.ScrapedProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceNormalizationServiceTest {

    private PriceNormalizationService service;

    @BeforeEach
    void setUp() {
        service = new PriceNormalizationService();
    }

    private ScrapedProductDto scraped(String title, String price, String weight) {
        return ScrapedProductDto.builder()
                .title(title).price(price).weight(weight)
                .source("Amazon").link("https://example.test/p").build();
    }

    @Test
    @DisplayName("computes price per kilogram for a 5 kg pack")
    void normalizesKilograms() {
        ProductDto result = service.normalizeProduct(
                scraped("Aashirvaad Superior MP Atta 5kg", "300", "5kg"));

        assertEquals(0, new BigDecimal("300.00").compareTo(result.getNumericPrice()));
        assertEquals(0, new BigDecimal("5").compareTo(result.getNormalizedWeight()));
        assertEquals("kg", result.getUnit());
        assertEquals(0, new BigDecimal("60.00").compareTo(result.getPricePerUnit()));
    }

    @Test
    @DisplayName("converts grams to kilograms before comparing")
    void normalizesGrams() {
        ProductDto result = service.normalizeProduct(scraped("Tata Salt 500 g", "25", "500 g"));

        assertEquals("kg", result.getUnit());
        assertEquals(0, new BigDecimal("0.5").compareTo(result.getNormalizedWeight()));
        assertEquals(0, new BigDecimal("50.00").compareTo(result.getPricePerUnit()));
    }

    @Test
    @DisplayName("converts millilitres to litres and reports the volume unit")
    void normalizesMillilitres() {
        ProductDto result = service.normalizeProduct(scraped("Fortune Oil 900 ml", "180", "900ml"));

        assertEquals("L", result.getUnit());
        assertEquals(0, new BigDecimal("200.00").compareTo(result.getPricePerUnit()));
    }

    @ParameterizedTest(name = "\"{0}\" is read as {1} {2}")
    @CsvSource({
            "'Amul Milk 1 litre', 1, L",
            "'Amul Milk 1 liter', 1, L",
            "'Coca Cola 2 L',     2, L",
            "'Basmati Rice 1KG',  1, kg",
            "'Sugar 2.5 kg',      2.5, kg",
    })
    @DisplayName("recognises the unit spellings the stores actually use")
    void recognisesUnitSpellings(String title, String expectedAmount, String expectedUnit) {
        ProductDto result = service.normalizeProduct(scraped(title, "100", null));

        assertEquals(expectedUnit, result.getUnit());
        assertEquals(0, new BigDecimal(expectedAmount).compareTo(result.getNormalizedWeight()));
    }

    @Test
    @DisplayName("falls back to the title when the weight field is missing")
    void fallsBackToTitle() {
        ProductDto result = service.normalizeProduct(
                scraped("Nescafe Classic Coffee 200 g Jar", "560", "N/A"));

        assertEquals("kg", result.getUnit());
        assertEquals(0, new BigDecimal("2800.00").compareTo(result.getPricePerUnit()));
    }

    @Test
    @DisplayName("does not mistake a storage size for a weight")
    void ignoresNonQuantityNumbers() {
        ProductDto result = service.normalizeProduct(scraped("SanDisk Ultra 128 GB Card", "999", null));

        assertNull(result.getUnit());
        assertNull(result.getPricePerUnit());
    }

    @ParameterizedTest(name = "price \"{0}\" is unusable")
    @ValueSource(strings = {"", "   ", "N/A", "NA", "Currently unavailable"})
    @DisplayName("returns null rather than zero when the price cannot be read")
    void unparseablePriceIsNullNotZero(String rawPrice) {
        ProductDto result = service.normalizeProduct(scraped("Tata Salt 1kg", rawPrice, "1kg"));

        assertNull(result.getNumericPrice(),
                "a zero here would sort an unreadable listing to the top of a cheapest-first ranking");
        assertNull(result.getPricePerUnit());
    }

    @ParameterizedTest(name = "\"{0}\" parses to {1}")
    @CsvSource({
            "'1299',      1299.00",
            "'1,299',     1299.00",
            "'1,299.',    1299.00",
            "'1299.50',   1299.50",
    })
    @DisplayName("parses the price formats the stores render")
    void parsesRealWorldPrices(String rawPrice, String expected) {
        ProductDto result = service.normalizeProduct(scraped("Item 1kg", rawPrice, "1kg"));

        assertNotNull(result.getNumericPrice(), "failed to parse " + rawPrice);
        assertEquals(0, new BigDecimal(expected).compareTo(result.getNumericPrice()));
    }

    @Test
    @DisplayName("keeps the original price string for display")
    void preservesDisplayPrice() {
        ProductDto result = service.normalizeProduct(scraped("Item 1kg", "1,299", "1kg"));

        assertEquals("1,299", result.getPrice());
    }

    @Test
    @DisplayName("normalising a list skips nulls without failing the batch")
    void normalizesListDefensively() {
        List<ProductDto> results = service.normalizePrices(
                Arrays.asList(scraped("Rice 1kg", "60", "1kg"), null, scraped("Dal 1kg", "120", "1kg")));

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(product -> product.getPricePerUnit() != null));
    }

    @Test
    @DisplayName("an empty or null input produces an empty list")
    void handlesEmptyInput() {
        assertTrue(service.normalizePrices(null).isEmpty());
        assertTrue(service.normalizePrices(List.of()).isEmpty());
    }
}
