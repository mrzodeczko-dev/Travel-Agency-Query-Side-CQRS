package com.rzodeczko.infrastructure.persistence.document;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityDocumentTest {

    @Test
    void shouldBuildIdFromHotelIdAndDate() {
        String id = AvailabilityDocument.buildId(42L, LocalDate.of(2024, 6, 15));
        assertThat(id).isEqualTo("hotel_42_2024-06-15");
    }

    @Test
    void shouldBuildConsistentIdForSameInput() {
        LocalDate date = LocalDate.of(2024, 1, 1);
        String id1 = AvailabilityDocument.buildId(1L, date);
        String id2 = AvailabilityDocument.buildId(1L, date);
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    void shouldBuildDifferentIdsForDifferentHotels() {
        LocalDate date = LocalDate.of(2024, 6, 1);
        String id1 = AvailabilityDocument.buildId(1L, date);
        String id2 = AvailabilityDocument.buildId(2L, date);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void shouldBuildDifferentIdsForDifferentDates() {
        String id1 = AvailabilityDocument.buildId(1L, LocalDate.of(2024, 6, 1));
        String id2 = AvailabilityDocument.buildId(1L, LocalDate.of(2024, 6, 2));
        assertThat(id1).isNotEqualTo(id2);
    }
}
