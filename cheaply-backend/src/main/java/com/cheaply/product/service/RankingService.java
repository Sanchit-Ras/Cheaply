package com.cheaply.product.service;

import com.cheaply.product.dto.ProductDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orders search results cheapest-first, within comparable units.
 *
 * <p>The previous implementation sorted every product into a single list by
 * price per unit regardless of unit, which put rupees-per-kilogram and
 * rupees-per-litre in the same ordering and presented the result as if it were
 * a like-for-like comparison. Rice at 60/kg is not "cheaper" than oil at
 * 150/L in any sense a shopper cares about.
 *
 * <p>Products are therefore grouped by unit, ranked within their group, and the
 * groups concatenated so the response is still a flat list. Each product
 * carries its rank and a bestValue flag, so a client can either render the flat
 * list or regroup by the unit field.
 */
@Service
public class RankingService {

    /**
     * Groups are emitted in this order so results are stable and predictable
     * rather than dependent on which store happened to respond first.
     */
    private static final List<String> UNIT_ORDER =
            List.of(PriceNormalizationService.UNIT_KG, PriceNormalizationService.UNIT_LITRE);

    private static final Comparator<ProductDto> BY_PRICE_PER_UNIT =
            Comparator.comparing(ProductDto::getPricePerUnit,
                    Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()));

    public List<ProductDto> rankProducts(List<ProductDto> products) {
        if (products == null || products.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<ProductDto>> byUnit = new LinkedHashMap<>();
        List<ProductDto> uncomparable = new ArrayList<>();

        for (ProductDto product : products) {
            if (product == null) {
                continue;
            }
            if (product.getPricePerUnit() == null || product.getUnit() == null) {
                // No usable price or no quantity - cannot be ranked against
                // anything, so it goes to the end rather than being dropped.
                // Users still want to see the listing.
                uncomparable.add(product);
            } else {
                byUnit.computeIfAbsent(product.getUnit(), key -> new ArrayList<>()).add(product);
            }
        }

        List<ProductDto> ranked = new ArrayList<>(products.size());
        for (String unit : orderedUnits(byUnit.keySet())) {
            List<ProductDto> group = byUnit.get(unit);
            group.sort(BY_PRICE_PER_UNIT);

            for (int i = 0; i < group.size(); i++) {
                ProductDto product = group.get(i);
                product.setRank(i + 1);
                product.setBestValue(i == 0);
            }
            ranked.addAll(group);
        }

        for (ProductDto product : uncomparable) {
            product.setRank(null);
            product.setBestValue(false);
        }
        ranked.addAll(uncomparable);

        return ranked;
    }

    /**
     * Known units first in a fixed order, then anything unexpected
     * alphabetically, so an unforeseen unit still produces deterministic output
     * instead of disappearing.
     */
    private List<String> orderedUnits(java.util.Set<String> presentUnits) {
        List<String> ordered = new ArrayList<>();
        for (String unit : UNIT_ORDER) {
            if (presentUnits.contains(unit)) {
                ordered.add(unit);
            }
        }
        presentUnits.stream()
                .filter(unit -> !UNIT_ORDER.contains(unit))
                .sorted()
                .forEach(ordered::add);
        return ordered;
    }
}
