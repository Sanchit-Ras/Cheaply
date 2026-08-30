package com.cheaply.history.repository;

import com.cheaply.history.model.SearchHistory;
import com.cheaply.user.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    /**
     * Paged so the caller can ask for the handful of rows it actually needs.
     * The unpaged version was fetching a user's entire history and discarding
     * all but the first few in Java.
     */
    List<SearchHistory> findByUserOrderBySearchedAtDesc(User user, Pageable pageable);

    Optional<SearchHistory> findByUserAndQueryIgnoreCase(User user, String query);

    /**
     * Ids of a user's history beyond the newest {@code pageable.offset} rows,
     * used to trim the history to its cap without loading the rows themselves.
     */
    @Query("SELECT sh.id FROM SearchHistory sh WHERE sh.user = :user ORDER BY sh.searchedAt DESC")
    List<Long> findIdsByUserOrderBySearchedAtDesc(@Param("user") User user, Pageable pageable);

    void deleteAllByIdIn(Collection<Long> ids);

    void deleteAllByUser(User user);
}
