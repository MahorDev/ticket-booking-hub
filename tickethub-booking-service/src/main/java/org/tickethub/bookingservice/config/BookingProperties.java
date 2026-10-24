package org.tickethub.bookingservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "booking")
public class BookingProperties {
    private int lockMinutes = 5;
    private int maxSeats = 6;
    private long expiryCheckMs = 10000;

    public int getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(int lockMinutes) {
        this.lockMinutes = lockMinutes;
    }

    public int getMaxSeats() {
        return maxSeats;
    }

    public void setMaxSeats(int maxSeats) {
        this.maxSeats = maxSeats;
    }

    public long getExpiryCheckMs() {
        return expiryCheckMs;
    }

    public void setExpiryCheckMs(long expiryCheckMs) {
        this.expiryCheckMs = expiryCheckMs;
    }
}
