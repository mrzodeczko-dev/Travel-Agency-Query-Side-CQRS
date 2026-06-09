package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.port.in.GetAvailabilityUseCase;
import com.rzodeczko.domain.model.Availability;
import com.rzodeczko.presentation.dto.AvailabilityResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {
    private final GetAvailabilityUseCase getAvailabilityUseCase;

    @GetMapping("/{hotelId}")
    public ResponseEntity<List<AvailabilityResponseDto>> getAvailability(
            @PathVariable long hotelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<Availability> result = getAvailabilityUseCase.getForHotel(hotelId, from, to);
        List<AvailabilityResponseDto> body = result.stream()
                .map(a -> new AvailabilityResponseDto(
                        a.getHotelId(),
                        a.getDate(),
                        a.getOccupied(),
                        a.getCapacity(),
                        a.freeRooms(),
                        a.getStatus()
                )).toList();

        return ResponseEntity.ok(body);

    }
}
