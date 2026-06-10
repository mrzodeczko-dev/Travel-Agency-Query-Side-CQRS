package com.rzodeczko.application.command;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateAvailabilityCommandTest {

    @Test
    void shouldCreateCommandWithValidArguments() {
        var command = new UpdateAvailabilityCommand(1L, LocalDate.of(2024, 6, 1), 50);

        assertThat(command.hotelId()).isEqualTo(1L);
        assertThat(command.date()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(command.occupied()).isEqualTo(50);
    }

    @Test
    void shouldThrowWhenDateIsNull() {
        assertThatThrownBy(() -> new UpdateAvailabilityCommand(1L, null, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Date");
    }

    @Test
    void shouldAllowZeroOccupied() {
        var command = new UpdateAvailabilityCommand(1L, LocalDate.of(2024, 6, 1), 0);
        assertThat(command.occupied()).isZero();
    }
}
