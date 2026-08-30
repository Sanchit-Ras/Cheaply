package com.cheaply.history.service;

import com.cheaply.history.dto.SearchHistoryDto;
import com.cheaply.history.model.SearchHistory;
import com.cheaply.history.repository.SearchHistoryRepository;
import com.cheaply.user.model.User;
import com.cheaply.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Keeps a capped, most-recent-first list of each user's search queries.
 *
 * <p>A repeated query updates the existing row's timestamp rather than adding a
 * duplicate, so the history reads as a list of distinct things the user looked
 * for. The cap is enforced on insert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchHistoryService {

    /** How many entries a single user's history is allowed to keep. */
    static final int MAX_HISTORY_ITEMS = 20;

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;

    @Transactional
    public void saveSearch(String username, String rawQuery) {
        if (username == null || rawQuery == null || rawQuery.trim().isEmpty()) {
            return;
        }

        String query = rawQuery.trim();
        Optional<User> maybeUser = userRepository.findByUsernameIgnoreCase(username);
        if (maybeUser.isEmpty()) {
            return;
        }
        User user = maybeUser.get();

        try {
            Optional<SearchHistory> existing = searchHistoryRepository.findByUserAndQueryIgnoreCase(user, query);

            if (existing.isPresent()) {
                SearchHistory history = existing.get();
                history.setSearchedAt(LocalDateTime.now());
                searchHistoryRepository.save(history);
                return;
            }

            searchHistoryRepository.save(SearchHistory.builder()
                    .user(user)
                    .query(query)
                    .searchedAt(LocalDateTime.now())
                    .build());

            trimHistoryToMax(user);

        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent identical search from the same
            // user. The database's unique index did its job; there is nothing
            // left to do because the row now exists either way.
            log.debug("Concurrent duplicate search history insert for '{}'", username);
        } catch (Exception e) {
            log.warn("Could not persist search history for '{}': {}", username, e.getMessage());
        }
    }

    /**
     * Deletes anything past the cap. Because trimming runs on every insert, at
     * most a handful of rows are ever over the line, so one page of ids beyond
     * the cap is sufficient.
     */
    private void trimHistoryToMax(User user) {
        Pageable beyondCap = PageRequest.of(1, MAX_HISTORY_ITEMS);
        List<Long> excessIds = searchHistoryRepository.findIdsByUserOrderBySearchedAtDesc(user, beyondCap);
        if (!excessIds.isEmpty()) {
            searchHistoryRepository.deleteAllByIdIn(excessIds);
            log.debug("Trimmed {} history entries for '{}'", excessIds.size(), user.getUsername());
        }
    }

    @Transactional(readOnly = true)
    public List<SearchHistoryDto> getRecentSearches(String username, int limit) {
        if (username == null) {
            return Collections.emptyList();
        }

        int effectiveLimit = normalizeLimit(limit);

        return userRepository.findByUsernameIgnoreCase(username)
                .map(user -> searchHistoryRepository
                        .findByUserOrderBySearchedAtDesc(user, PageRequest.of(0, effectiveLimit))
                        .stream()
                        .map(history -> SearchHistoryDto.builder()
                                .id(history.getId())
                                .query(history.getQuery())
                                .searchedAt(history.getSearchedAt())
                                .build())
                        .toList())
                .orElse(Collections.emptyList());
    }

    @Transactional(readOnly = true)
    public List<String> getRecentSearchQueries(String username, int limit) {
        return getRecentSearches(username, limit).stream()
                .map(SearchHistoryDto::getQuery)
                .toList();
    }

    @Transactional
    public void clearSearchHistory(String username) {
        userRepository.findByUsernameIgnoreCase(username).ifPresent(user -> {
            searchHistoryRepository.deleteAllByUser(user);
            log.info("Cleared search history for '{}'", username);
        });
    }

    /**
     * Clamps a caller-supplied limit. A request for zero, a negative number, or
     * more rows than we store is answered with the cap rather than an error.
     */
    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return MAX_HISTORY_ITEMS;
        }
        return Math.min(limit, MAX_HISTORY_ITEMS);
    }
}
