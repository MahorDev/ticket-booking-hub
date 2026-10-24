package com.tickethub.event.repository;

import com.tickethub.event.entity.Event;
import com.tickethub.event.enums.EventStatus;
import com.tickethub.event.enums.EventType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("""
            SELECT e
            FROM Event e
            WHERE (:title IS NULL
                   OR LOWER(e.title) LIKE LOWER(CONCAT('%', :title, '%')))
              AND (:eventType IS NULL
                   OR e.eventType = :eventType)
              AND (:genre IS NULL
                   OR LOWER(e.genre) LIKE LOWER(CONCAT('%', :genre, '%')))
              AND (:language IS NULL
                   OR LOWER(e.language) LIKE LOWER(CONCAT('%', :language, '%')))
              AND (:status IS NULL
                   OR e.status = :status)
            """)
    Page<Event> search(
            @Param("title") String title,
            @Param("eventType") EventType eventType,
            @Param("genre") String genre,
            @Param("language") String language,
            @Param("status") EventStatus status,
            Pageable pageable
    );
}