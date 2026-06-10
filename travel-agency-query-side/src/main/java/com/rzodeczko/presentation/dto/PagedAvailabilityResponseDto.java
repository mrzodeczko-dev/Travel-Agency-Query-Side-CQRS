package com.rzodeczko.presentation.dto;

import java.util.List;

public record PagedAvailabilityResponseDto(
        List<AvailabilityResponseDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
