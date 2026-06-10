package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.port.in.GetAvailabilityUseCase;
import com.rzodeczko.domain.model.Availability;
import com.rzodeczko.presentation.dto.AvailabilityResponseDto;
import com.rzodeczko.presentation.dto.PagedAvailabilityResponseDto;
import com.rzodeczko.presentation.exception.InvalidDateRangeException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.Range;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {
    private final GetAvailabilityUseCase getAvailabilityUseCase;

    @GetMapping("/{hotelId}")
    public ResponseEntity<PagedAvailabilityResponseDto> getAvailability(
            @PathVariable long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Range(min = 1, max = 100) int size) {

        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException(from, to);
        }

        List<Availability> result = getAvailabilityUseCase.getForHotel(hotelId, from, to, page, size);
        long totalElements = getAvailabilityUseCase.countForHotel(hotelId, from, to);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<AvailabilityResponseDto> content = result.stream()
                .map(a -> new AvailabilityResponseDto(
                        a.getHotelId(),
                        a.getDate(),
                        a.getOccupied(),
                        a.getCapacity(),
                        a.freeRooms(),
                        a.getStatus()
                )).toList();

        return ResponseEntity.ok(new PagedAvailabilityResponseDto(content, page, size, totalElements, totalPages));
    }
}
