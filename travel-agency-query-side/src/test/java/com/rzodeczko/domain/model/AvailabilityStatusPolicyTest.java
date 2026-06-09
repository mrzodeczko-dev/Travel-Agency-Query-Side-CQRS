package com.rzodeczko.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvailabilityStatusPolicyTest {

    private final AvailabilityStatusPolicy policy = new AvailabilityStatusPolicy(0.9);


    @Test
    void shouldThrowWhenThresholdIsZero() {
        assertThatThrownBy(() -> new AvailabilityStatusPolicy(0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastRoomsThreshold");
    }

    @Test
    void shouldThrowWhenThresholdExceedsOne() {
        assertThatThrownBy(() -> new AvailabilityStatusPolicy(1.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lastRoomsThreshold");
    }

    @Test
    void shouldAcceptThresholdOfOne() {
        AvailabilityStatusPolicy fullPolicy = new AvailabilityStatusPolicy(1.0);
        assertThat(fullPolicy.lastRoomsThreshold()).isEqualTo(1.0);
    }


    @Test
    void shouldReturnAvailableWhenOccupiedIsZero() {
        assertThat(policy.evaluate(0, 100)).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void shouldReturnAvailableWhenOccupiedBelowThreshold() {
        // threshold=0.9, capacity=100 → LAST_ROOMS starts at floor(90)=90
        assertThat(policy.evaluate(89, 100)).isEqualTo(AvailabilityStatus.AVAILABLE);
    }


    @Test
    void shouldReturnLastRoomsWhenOccupiedExactlyAtThreshold() {
        assertThat(policy.evaluate(90, 100)).isEqualTo(AvailabilityStatus.LAST_ROOMS);
    }

    @Test
    void shouldReturnLastRoomsWhenOccupiedBetweenThresholdAndCapacity() {
        assertThat(policy.evaluate(95, 100)).isEqualTo(AvailabilityStatus.LAST_ROOMS);
    }

    @Test
    void shouldReturnLastRoomsOneBeforeFullCapacity() {
        assertThat(policy.evaluate(99, 100)).isEqualTo(AvailabilityStatus.LAST_ROOMS);
    }


    @Test
    void shouldReturnSoldOutWhenFullyOccupied() {
        assertThat(policy.evaluate(100, 100)).isEqualTo(AvailabilityStatus.SOLD_OUT);
    }

    @Test
    void shouldReturnSoldOutWhenOverbooked() {
        assertThat(policy.evaluate(110, 100)).isEqualTo(AvailabilityStatus.SOLD_OUT);
    }


    @ParameterizedTest
    @CsvSource({
            "0,10,AVAILABLE",
            "8,10,AVAILABLE",
            "9,10,LAST_ROOMS",
            "9,10,LAST_ROOMS",
            "10,10,SOLD_OUT",
            "11,10,SOLD_OUT",
    })
    void shouldEvaluateStatusCorrectly(long occupied, long capacity, AvailabilityStatus expected) {
        assertThat(policy.evaluate(occupied, capacity)).isEqualTo(expected);
    }
}
