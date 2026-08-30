package com.cheaply.search.controller;

import com.cheaply.exception.GlobalExceptionHandler;
import com.cheaply.exception.ScraperUnavailableException;
import com.cheaply.product.dto.ProductDto;
import com.cheaply.scraper.dto.StoreStatusDto;
import com.cheaply.search.dto.SearchRequest;
import com.cheaply.search.dto.SearchResponse;
import com.cheaply.search.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private SearchService searchService;

    @InjectMocks
    private SearchController searchController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(searchController)
                .setControllerAdvice(new GlobalExceptionHandler())
                // The controller takes an @AuthenticationPrincipal, which a
                // standalone setup does not resolve on its own.
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();
    }

    private String body(String query) throws Exception {
        return objectMapper.writeValueAsString(SearchRequest.builder().query(query).build());
    }

    @Test
    @DisplayName("POST /api/search - returns ranked products")
    void returnsRankedProducts() throws Exception {
        SearchResponse response = SearchResponse.builder()
                .query("salt")
                .totalResults(1)
                .cached(false)
                .partial(false)
                .stores(List.of(StoreStatusDto.builder().name("Amazon").status("ok").count(1).build()))
                .products(List.of(ProductDto.builder()
                        .title("Tata Salt 1kg")
                        .pricePerUnit(new BigDecimal("25.00"))
                        .unit("kg")
                        .rank(1)
                        .bestValue(true)
                        .build()))
                .build();

        when(searchService.search(any(SearchRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("salt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalResults").value(1))
                .andExpect(jsonPath("$.data.products[0].price_per_unit").value(25.00))
                .andExpect(jsonPath("$.data.products[0].bestValue").value(true));
    }

    @Test
    @DisplayName("POST /api/search - surfaces a partial result and the failing store")
    void surfacesPartialResults() throws Exception {
        SearchResponse response = SearchResponse.builder()
                .query("salt")
                .totalResults(1)
                .partial(true)
                .stores(List.of(
                        StoreStatusDto.builder().name("Amazon").status("ok").count(1).build(),
                        StoreStatusDto.builder().name("Flipkart").status("failed").error("timeout").build()))
                .products(List.of(ProductDto.builder().title("Tata Salt 1kg").build()))
                .build();

        when(searchService.search(any(SearchRequest.class), any())).thenReturn(response);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("salt")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partial").value(true))
                .andExpect(jsonPath("$.data.stores[1].status").value("failed"));
    }

    @Test
    @DisplayName("POST /api/search - rejects a blank query with 400")
    void rejectsBlankQuery() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/search - maps a scraper outage to 503")
    void mapsScraperOutageTo503() throws Exception {
        when(searchService.search(any(SearchRequest.class), any()))
                .thenThrow(new ScraperUnavailableException("The product search service is unavailable."));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("salt")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/search - rejects a malformed body with 400 rather than 500")
    void rejectsMalformedBody() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
