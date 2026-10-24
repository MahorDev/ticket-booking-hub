package com.tickethub.event.repository;

import com.tickethub.event.entity.Screen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

    @Override
    @EntityGraph(attributePaths = {"venue"})
    Page<Screen> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"venue"})
    Page<Screen> findByVenueVenueId(
            Long venueId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"venue"})
    List<Screen> findAllByVenueVenueId(Long venueId);

    boolean existsByVenueVenueId(
            Long venueId
    );

    boolean existsByVenueVenueIdAndScreenNameIgnoreCase(
            Long venueId,
            String screenName
    );
}