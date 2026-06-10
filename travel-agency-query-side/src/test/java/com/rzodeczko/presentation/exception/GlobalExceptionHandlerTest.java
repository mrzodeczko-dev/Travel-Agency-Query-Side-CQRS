package com.rzodeczko.presentation.exception;

import com.rzodeczko.domain.exception.AvailabilityNotFoundException;
import com.rzodeczko.presentation.dto.ErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturn404ForAvailabilityNotFoundException() {
        var exception = new AvailabilityNotFoundException("Hotel 1 not found");

        ResponseEntity<ErrorResponseDto> response = handler.handleNotFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Hotel 1 not found");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void shouldReturn400ForInvalidDateRangeException() {
        var exception = new InvalidDateRangeException(
                LocalDate.of(2024, 6, 10), LocalDate.of(2024, 6, 1));

        ResponseEntity<ErrorResponseDto> response = handler.handleInvalidDateRange(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).contains("Invalid date range");
    }

    @Test
    void shouldReturn500ForUnexpectedException() {
        var exception = new RuntimeException("Something went wrong");

        ResponseEntity<ErrorResponseDto> response = handler.handleException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Internal server error");
        assertThat(response.getBody().validationErrors()).isNull();
    }
}
