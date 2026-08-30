package com.cheaply.history.controller;

import com.cheaply.common.ApiResponse;
import com.cheaply.history.dto.SearchHistoryDto;
import com.cheaply.history.service.SearchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Search History", description = "The authenticated user's recent searches")
public class SearchHistoryController {

    private final SearchHistoryService searchHistoryService;

    @GetMapping
    @Operation(summary = "Recent searches", description = "Most recent first, capped at 20")
    public ResponseEntity<ApiResponse<List<SearchHistoryDto>>> getSearchHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(name = "limit", defaultValue = "20")
            @Min(value = 1, message = "limit must be at least 1")
            @Max(value = 20, message = "limit cannot exceed 20") int limit
    ) {
        List<SearchHistoryDto> history = searchHistoryService.getRecentSearches(userDetails.getUsername(), limit);
        return ResponseEntity.ok(ApiResponse.success("Search history retrieved", history));
    }

    @DeleteMapping
    @Operation(summary = "Clear history", description = "Deletes every stored search for this user")
    public ResponseEntity<ApiResponse<Void>> clearSearchHistory(@AuthenticationPrincipal UserDetails userDetails) {
        searchHistoryService.clearSearchHistory(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Search history cleared successfully"));
    }
}
