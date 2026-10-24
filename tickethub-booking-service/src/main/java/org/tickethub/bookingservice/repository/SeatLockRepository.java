package org.tickethub.bookingservice.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.tickethub.bookingservice.entity.SeatLock;
import org.tickethub.bookingservice.entity.enums.SeatLockStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatLockRepository extends JpaRepository<SeatLock, Long> {
    Optional<SeatLock> findByShowIdAndSeatId(Long showId, Long seatId);
    List<SeatLock> findByShowIdAndStatus(Long showId, SeatLockStatus status);
    List<SeatLock> findByBookingId(Long bookingId);

    @Modifying
    @Query(value = """
        DELETE FROM seat_locks
        WHERE show_id = :showId
          AND seat_id = :seatId
          AND (status <> 'LOCKED' OR expires_at <= :now)
        """, nativeQuery = true)
    int deleteIfReleasedOrExpired(
        @Param("showId") Long showId,
        @Param("seatId") Long seatId,
        @Param("now") LocalDateTime now
    );

    @Modifying
    @Query(value = """
        INSERT INTO seat_locks
            (show_id, seat_id, user_id, booking_id, status, locked_at, expires_at, created_at, updated_at)
        VALUES
            (:showId, :seatId, :userId, :bookingId, 'LOCKED', :lockedAt, :expiresAt, :createdAt, :updatedAt)
        ON DUPLICATE KEY UPDATE id = id
        """, nativeQuery = true)
    int tryAcquire(
        @Param("showId") Long showId,
        @Param("seatId") Long seatId,
        @Param("userId") Long userId,
        @Param("bookingId") Long bookingId,
        @Param("lockedAt") LocalDateTime lockedAt,
        @Param("expiresAt") LocalDateTime expiresAt,
        @Param("createdAt") LocalDateTime createdAt,
        @Param("updatedAt") LocalDateTime updatedAt
    );

    @Modifying
    @Query(value = """
        UPDATE seat_locks
        SET booking_id = :bookingId, expires_at = :expiresAt, updated_at = :now
        WHERE show_id = :showId
          AND seat_id = :seatId
          AND user_id = :userId
          AND status = 'LOCKED'
          AND booking_id IS NULL
          AND expires_at > :now
        """, nativeQuery = true)
    int attachToBooking(
        @Param("showId") Long showId,
        @Param("seatId") Long seatId,
        @Param("userId") Long userId,
        @Param("bookingId") Long bookingId,
        @Param("expiresAt") LocalDateTime expiresAt,
        @Param("now") LocalDateTime now
    );


    @Modifying
    @Query(value = """
        UPDATE seat_locks
        SET expires_at = :permanentExpiry, updated_at = :now
        WHERE booking_id = :bookingId AND status = 'LOCKED'
        """, nativeQuery = true)
    int makePermanentForBooking(
        @Param("bookingId") Long bookingId,
        @Param("permanentExpiry") LocalDateTime permanentExpiry,
        @Param("now") LocalDateTime now
    );

    @Modifying
    @Query(value = """
        UPDATE seat_locks
        SET status = 'RELEASED', updated_at = :now
        WHERE booking_id = :bookingId AND status = 'LOCKED'
        """, nativeQuery = true)
    int releaseByBookingId(@Param("bookingId") Long bookingId, @Param("now") LocalDateTime now);

    @Modifying
    @Query(value = """
        UPDATE seat_locks
        SET status = 'RELEASED', updated_at = :now
        WHERE show_id = :showId
          AND seat_id IN (:seatIds)
          AND user_id = :userId
          AND status = 'LOCKED'
          AND booking_id IS NULL
        """, nativeQuery = true)
    int releaseByUserAndSeats(
        @Param("showId") Long showId,
        @Param("seatIds") List<Long> seatIds,
        @Param("userId") Long userId,
        @Param("now") LocalDateTime now
    );

    @Modifying
    @Query(value = """
        UPDATE seat_locks
        SET status = 'EXPIRED', updated_at = :now
        WHERE status = 'LOCKED' AND booking_id IS NULL AND expires_at <= :now
        """, nativeQuery = true)
    int expireLocks(@Param("now") LocalDateTime now);
}
