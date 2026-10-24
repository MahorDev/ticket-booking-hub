package com.tickethub.event.repository;

import com.tickethub.event.entity.Seat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Override
    @EntityGraph(attributePaths = {"screen"})
    Page<Seat> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"screen"})
    Page<Seat> findByScreenScreenId(
            Long screenId,
            Pageable pageable
    );

    boolean existsByScreenScreenId(
            Long screenId
    );

    @EntityGraph(attributePaths = {"screen"})
    List<Seat> findAllByScreenScreenId(Long screenId);

    @EntityGraph(attributePaths = {"screen"})
    List<Seat> findAllByScreenScreenIdOrderByRowNameAscSeatNumberAsc(Long screenId);

    boolean existsByScreenScreenIdAndSeatNumberIgnoreCase(
            Long screenId,
            String seatNumber
    );
}