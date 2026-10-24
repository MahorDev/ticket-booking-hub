package com.tickethub.event.repository;

import com.tickethub.event.entity.ShowSeat;
import com.tickethub.event.enums.ShowSeatStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    @EntityGraph(attributePaths = {"seat", "show"})
    Page<ShowSeat> findByShowShowId(Long showId, Pageable pageable);

    @EntityGraph(attributePaths = {"seat", "show"})
    List<ShowSeat> findAllByShowShowIdOrderByShowSeatId(Long showId);

    boolean existsByShowShowId(Long showId);

    boolean existsByShowShowIdAndStatus(Long showId, ShowSeatStatus status);

    void deleteByShowShowId(Long showId);

    boolean existsBySeatSeatId(Long seatId);

    boolean existsByShowShowIdAndSeatSeatId(Long showId, Long seatId);
}