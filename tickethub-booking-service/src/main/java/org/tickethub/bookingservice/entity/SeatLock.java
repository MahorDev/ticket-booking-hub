package org.tickethub.bookingservice.entity;

import jakarta.persistence.*;
import org.tickethub.bookingservice.entity.enums.SeatLockStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "seat_locks",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_seat_lock_show_seat",
           columnNames = {"show_id", "seat_id"}),
       indexes = {
           @Index(name = "idx_lock_show", columnList = "show_id"),
           @Index(name = "idx_lock_expiry", columnList = "expires_at"),
           @Index(name = "idx_lock_booking", columnList = "booking_id")
       })
public class SeatLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatLockStatus status;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getShowId() { return showId; }
    public Long getSeatId() { return seatId; }
    public Long getUserId() { return userId; }
    public Long getBookingId() { return bookingId; }
    public SeatLockStatus getStatus() { return status; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setShowId(Long showId) { this.showId = showId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public void setStatus(SeatLockStatus status) { this.status = status; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
