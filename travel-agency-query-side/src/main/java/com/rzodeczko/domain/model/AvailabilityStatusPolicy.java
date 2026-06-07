package com.rzodeczko.domain.model;

public record AvailabilityStatusPolicy(double lastRoomsThreshold) {
    public AvailabilityStatusPolicy {
        if (lastRoomsThreshold <= 0 || lastRoomsThreshold > 1) {
            throw new IllegalArgumentException("lastRoomsThreshold must be in (0, 1]");
        }
    }

    public AvailabilityStatus evaluate(long occupied, long capacity) {
        if (occupied >= capacity) {
            return AvailabilityStatus.SOLD_OUT;
        }

        if (occupied >= Math.floor(capacity * lastRoomsThreshold)) {
            return AvailabilityStatus.LAST_ROOMS;
        }

        return AvailabilityStatus.AVAILABLE;
    }
}
