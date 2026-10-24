package com.tickethub.event.repository;

import com.tickethub.event.entity.Show;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {

 @Override
 @EntityGraph(attributePaths = {"event", "screen", "screen.venue"})
 Page<Show> findAll(Pageable pageable);

 @EntityGraph(attributePaths = {"event", "screen", "screen.venue"})
 Page<Show> findByEventEventId(
         Long eventId,
         Pageable pageable
 );

 @EntityGraph(attributePaths = {"event", "screen", "screen.venue"})
 List<Show> findAllByEventEventId(Long eventId);

 @EntityGraph(attributePaths = {"event", "screen", "screen.venue"})
 Page<Show> findByScreenScreenId(
         Long screenId,
         Pageable pageable
 );

 @EntityGraph(attributePaths = {"event", "screen", "screen.venue"})
 List<Show> findAllByScreenScreenId(Long screenId);

 boolean existsByEventEventId(
         Long eventId
 );

 boolean existsByScreenScreenId(
         Long screenId
 );

 @Query("""
            SELECT COUNT(s)
            FROM Show s
            WHERE s.screen.screenId = :screenId
              AND s.showDate = :showDate
              AND s.startTime < :endTime
              AND s.endTime > :startTime
              AND s.status <> com.tickethub.event.enums.ShowStatus.CANCELLED
            """)
 long countOverlappingShows(
         @Param("screenId") Long screenId,
         @Param("showDate") LocalDate showDate,
         @Param("startTime") LocalTime startTime,
         @Param("endTime") LocalTime endTime
 );

 @Query("""
            SELECT COUNT(s)
            FROM Show s
            WHERE s.screen.screenId = :screenId
              AND s.showDate = :showDate
              AND s.startTime < :endTime
              AND s.endTime > :startTime
              AND s.showId <> :showId
              AND s.status <> com.tickethub.event.enums.ShowStatus.CANCELLED
            """)
 long countOverlappingShowsForUpdate(
         @Param("screenId") Long screenId,
         @Param("showDate") LocalDate showDate,
         @Param("startTime") LocalTime startTime,
         @Param("endTime") LocalTime endTime,
         @Param("showId") Long showId
 );
}