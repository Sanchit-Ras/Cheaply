package com.cheaply.search.controller;

import com.cheaply.common.ApiResponse;
import com.cheaply.search.dto.SearchRequest;
import com.cheaply.search.dto.SearchResponse;
import com.cheaply.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Multi-store grocery price comparison")
public class SearchController {

    private final SearchService searchService;

    /**
     * Open to anonymous callers by design - browsing prices should not require
     * an account. Authenticated callers additionally get their search recorded
     * and their recent queries returned.
     *
     * <p>The principal is injected rather than read from the SecurityContext by
     * hand; {@code @AuthenticationPrincipal} is simply null when the request is
     * anonymous, which removes the string comparison against "anonymousUser"
     * this method used to rely on.
     */
    @PostMapping
    @Operation(summary = "Search products",
            description = "Scrapes the configured stores (or serves a cached result), normalises "
                    + "prices to a common unit, and ranks the results cheapest-first. "
                    + "Rate limited per client.")
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @Valid @RequestBody SearchRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails != null ? userDetails.getUsername() : null;
        SearchResponse response = searchService.search(request, username);
        return ResponseEntity.ok(ApiResponse.success("Search completed successfully", response));
    }
}
