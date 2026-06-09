package com.rzodeczko.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailabilityTest {

    private static final LocalDate DATE = LocalDate.of(2024, 6, 1);

    // --- constructor validation ---

    @Test
    void shouldThrowWhenOccupiedIsNegative() {
        assertThatThrownBy(() -> new Availability(1L, DATE, -1, 100, AvailabilityStatus.AVAILABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Occupied");
    }

    @Test
    void shouldThrowWhenCapacityIsZero() {
        assertThatThrownBy(() -> new Availability(1L, DATE, 0, 0, AvailabilityStatus.AVAILABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Capacity");
    }

    @Test
    void shouldThrowWhenCapacityIsNegative() {
        assertThatThrownBy(() -> new Availability(1L, DATE, 0, -10, AvailabilityStatus.AVAILABLE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Capacity");
    }

    @Test
    void shouldThrowWhenStatusIsNull() {
        assertThatThrownBy(() -> new Availability(1L, DATE, 0, 100, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Status");
    }

    @Test
    void shouldCreateAvailabilityWithValidArguments() {
        Availability availability = new Availability(1L, DATE, 50, 100, AvailabilityStatus.AVAILABLE);

        assertThat(availability.getHotelId()).isEqualTo(1L);
        assertThat(availability.getDate()).isEqualTo(DATE);
        assertThat(availability.getOccupied()).isEqualTo(50);
        assertThat(availability.getCapacity()).isEqualTo(100);
        assertThat(availability.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    // --- freeRooms ---

    @Test
    void shouldCalculateFreeRooms() {
        Availability availability = new Availability(1L, DATE, 30, 100, AvailabilityStatus.AVAILABLE);
        assertThat(availability.freeRooms()).isEqualTo(70);
    }

    @Test
    void shouldReturnZeroFreeRoomsWhenFullyOccupied() {
        Availability availability = new Availability(1L, DATE, 100, 100, AvailabilityStatus.SOLD_OUT);
        assertThat(availability.freeRooms()).isZero();
    }

    @Test
    void shouldReturnZeroFreeRoomsWhenOverbooked() {
        Availability availability = new Availability(1L, DATE, 110, 100, AvailabilityStatus.SOLD_OUT);
        assertThat(availability.freeRooms()).isZero();
    }

    @Test
    void shouldReturnFullCapacityWhenNotOccupied() {
        Availability availability = new Availability(1L, DATE, 0, 100, AvailabilityStatus.AVAILABLE);
        assertThat(availability.freeRooms()).isEqualTo(100);
    }
}
