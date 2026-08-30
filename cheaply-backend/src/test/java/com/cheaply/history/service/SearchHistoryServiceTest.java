package com.cheaply.history.service;

import com.cheaply.history.dto.SearchHistoryDto;
import com.cheaply.history.model.SearchHistory;
import com.cheaply.history.repository.SearchHistoryRepository;
import com.cheaply.user.model.Role;
import com.cheaply.user.model.User;
import com.cheaply.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchHistoryServiceTest {

    @Mock private SearchHistoryRepository searchHistoryRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private SearchHistoryService searchHistoryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L).username("testuser").email("test@example.com")
                .password("hashed").role(Role.ROLE_USER).build();
    }

    @Test
    @DisplayName("a new query is inserted")
    void savesNewQuery() {
        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testUser));
        when(searchHistoryRepository.findByUserAndQueryIgnoreCase(testUser, "rice")).thenReturn(Optional.empty());
        when(searchHistoryRepository.findIdsByUserOrderBySearchedAtDesc(eq(testUser), any(Pageable.class)))
                .thenReturn(List.of());

        searchHistoryService.saveSearch("testuser", "rice");

        verify(searchHistoryRepository).save(any(SearchHistory.class));
    }

    @Test
    @DisplayName("a repeated query updates the existing row instead of duplicating it")
    void updatesExistingQuery() {
        SearchHistory existing = SearchHistory.builder()
                .id(5L).user(testUser).query("rice")
                .searchedAt(LocalDateTime.now().minusDays(1)).build();

        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testUser));
        when(searchHistoryRepository.findByUserAndQueryIgnoreCase(testUser, "rice")).thenReturn(Optional.of(existing));

        searchHistoryService.saveSearch("testuser", "rice");

        ArgumentCaptor<SearchHistory> captor = ArgumentCaptor.forClass(SearchHistory.class);
        verify(searchHistoryRepository).save(captor.capture());
        assertEquals(5L, captor.getValue().getId());
        verify(searchHistoryRepository, never())
                .findIdsByUserOrderBySearchedAtDesc(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("history beyond the cap is deleted after an insert")
    void trimsHistoryToCap() {
        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testUser));
        when(searchHistoryRepository.findByUserAndQueryIgnoreCase(any(), anyString())).thenReturn(Optional.empty());
        when(searchHistoryRepository.findIdsByUserOrderBySearchedAtDesc(eq(testUser), any(Pageable.class)))
                .thenReturn(List.of(101L, 102L));

        searchHistoryService.saveSearch("testuser", "rice");

        verify(searchHistoryRepository).deleteAllByIdIn(List.of(101L, 102L));
    }

    @Test
    @DisplayName("a lost race on the unique index is absorbed silently")
    void toleratesConcurrentDuplicate() {
        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testUser));
        when(searchHistoryRepository.findByUserAndQueryIgnoreCase(any(), anyString())).thenReturn(Optional.empty());
        when(searchHistoryRepository.save(any(SearchHistory.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        searchHistoryService.saveSearch("testuser", "rice");
    }

    @Test
    @DisplayName("the requested limit is pushed down to the query rather than applied in memory")
    void appliesLimitInTheQuery() {
        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testUser));
        when(searchHistoryRepository.findByUserOrderBySearchedAtDesc(eq(testUser), any(Pageable.class)))
                .thenReturn(List.of(SearchHistory.builder()
                        .id(1L).query("rice").searchedAt(LocalDateTime.now()).build()));

        List<SearchHistoryDto> history = searchHistoryService.getRecentSearches("testuser", 5);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(searchHistoryRepository).findByUserOrderBySearchedAtDesc(eq(testUser), captor.capture());
        assertEquals(5, captor.getValue().getPageSize());
        assertEquals(1, history.size());
    }

    @Test
    @DisplayName("an over-large limit is clamped to the cap")
    void clampsExcessiveLimit() {
        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testUser));
        when(searchHistoryRepository.findByUserOrderBySearchedAtDesc(eq(testUser), any(Pageable.class)))
                .thenReturn(List.of());

        searchHistoryService.getRecentSearches("testuser", 10_000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(searchHistoryRepository).findByUserOrderBySearchedAtDesc(eq(testUser), captor.capture());
        assertEquals(SearchHistoryService.MAX_HISTORY_ITEMS, captor.getValue().getPageSize());
    }

    @Test
    @DisplayName("a blank query is not recorded")
    void ignoresBlankQuery() {
        searchHistoryService.saveSearch("testuser", "   ");

        verifyNoInteractions(userRepository, searchHistoryRepository);
    }

    @Test
    @DisplayName("an unknown user yields an empty history")
    void unknownUserHasNoHistory() {
        when(userRepository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertTrue(searchHistoryService.getRecentSearches("ghost", 5).isEmpty());
    }

    @Test
    @DisplayName("clearing deletes every row for the user")
    void clearsHistory() {
        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(testUser));

        searchHistoryService.clearSearchHistory("testuser");

        verify(searchHistoryRepository).deleteAllByUser(testUser);
    }
}
