package org.tickethub.bookingservice.entity;

import jakarta.persistence.*;
import org.tickethub.bookingservice.entity.enums.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings",
       indexes = {
           @Index(name = "idx_booking_user", columnList = "user_id"),
           @Index(name = "idx_booking_show", columnList = "show_id"),
           @Index(name = "idx_booking_status", columnList = "status")
       },
       uniqueConstraints = {
           @UniqueConstraint(name = "uk_booking_idempotency", columnNames = "idempotency_key"),
           @UniqueConstraint(name = "uk_booking_reference", columnNames = "booking_reference")
       })
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "booking_reference", nullable = false, length = 40)
    private String bookingReference;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "customer_name", length = 150)
    private String customerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "aggregate_version", nullable = false)
    private Long aggregateVersion = 1L;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<BookingItem> items = new ArrayList<>();

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

    public void addItem(BookingItem item) {
        items.add(item);
        item.setBooking(this);
    }

    public void removeItem(BookingItem item) {
        items.remove(item);
        item.setBooking(null);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getShowId() { return showId; }
    public BookingStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public String getBookingReference() { return bookingReference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getPaymentReference() { return paymentReference; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerName() { return customerName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Long getAggregateVersion() { return aggregateVersion; }
    public List<BookingItem> getItems() { return items; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setShowId(Long showId) { this.showId = showId; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setBookingReference(String bookingReference) { this.bookingReference = bookingReference; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setAggregateVersion(Long aggregateVersion) { this.aggregateVersion = aggregateVersion; }
}
