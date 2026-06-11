package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.port.in.GetHotelCapacityUseCase;
import com.rzodeczko.presentation.dto.HotelCapacityResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final GetHotelCapacityUseCase getHotelCapacityUseCase;

    @GetMapping("/{hotelId}")
    public ResponseEntity<HotelCapacityResponseDto> getHotel(@PathVariable long hotelId) {
        return getHotelCapacityUseCase.getCapacity(hotelId)
                .stream()
                .mapToObj(capacity -> new HotelCapacityResponseDto(hotelId, capacity))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
