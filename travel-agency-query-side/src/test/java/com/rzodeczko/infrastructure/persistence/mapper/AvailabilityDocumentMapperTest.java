package com.rzodeczko.infrastructure.persistence.mapper;

import com.rzodeczko.domain.model.Availability;
import com.rzodeczko.domain.model.AvailabilityStatus;
import com.rzodeczko.infrastructure.persistence.document.AvailabilityDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityDocumentMapperTest {

    private final AvailabilityDocumentMapper mapper = new AvailabilityDocumentMapper();

    private static final long HOTEL_ID = 42L;
    private static final LocalDate DATE = LocalDate.of(2024, 6, 15);

    // ── toDocument ───────────────────────────────────────────────────────────

    @Test
    void shouldMapDomainToDocument() {
        Availability domain = new Availability(HOTEL_ID, DATE, 30, 100, AvailabilityStatus.AVAILABLE);

        AvailabilityDocument doc = mapper.toDocument(domain);

        assertThat(doc.getId()).isEqualTo("hotel_42_2024-06-15");
        assertThat(doc.getHotelId()).isEqualTo(HOTEL_ID);
        assertThat(doc.getDate()).isEqualTo(DATE);
        assertThat(doc.getOccupied()).isEqualTo(30);
        assertThat(doc.getCapacity()).isEqualTo(100);
        assertThat(doc.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(doc.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldSetUpdatedAtToCurrentTime() {
        Availability domain = new Availability(HOTEL_ID, DATE, 10, 50, AvailabilityStatus.AVAILABLE);
        Instant before = Instant.now();

        AvailabilityDocument doc = mapper.toDocument(domain);

        assertThat(doc.getUpdatedAt()).isBetween(before, Instant.now().plusSeconds(1));
    }

    @Test
    void shouldMapAllStatuses() {
        for (AvailabilityStatus status : AvailabilityStatus.values()) {
            Availability domain = new Availability(HOTEL_ID, DATE, 10, 100, status);
            AvailabilityDocument doc = mapper.toDocument(domain);
            assertThat(doc.getStatus()).isEqualTo(status);
        }
    }

    // ── toDomain ─────────────────────────────────────────────────────────────

    @Test
    void shouldMapDocumentToDomain() {
        AvailabilityDocument doc = AvailabilityDocument.builder()
                .id("hotel_42_2024-06-15")
                .hotelId(HOTEL_ID)
                .date(DATE)
                .occupied(90)
                .capacity(100)
                .status(AvailabilityStatus.LAST_ROOMS)
                .updatedAt(Instant.now())
                .build();

        Availability domain = mapper.toDomain(doc);

        assertThat(domain.getHotelId()).isEqualTo(HOTEL_ID);
        assertThat(domain.getDate()).isEqualTo(DATE);
        assertThat(domain.getOccupied()).isEqualTo(90);
        assertThat(domain.getCapacity()).isEqualTo(100);
        assertThat(domain.getStatus()).isEqualTo(AvailabilityStatus.LAST_ROOMS);
    }

    // ── round-trip ───────────────────────────────────────────────────────────

    @Test
    void shouldPreserveDataOnRoundTrip() {
        Availability original = new Availability(HOTEL_ID, DATE, 50, 200, AvailabilityStatus.AVAILABLE);

        AvailabilityDocument doc = mapper.toDocument(original);
        Availability restored = mapper.toDomain(doc);

        assertThat(restored.getHotelId()).isEqualTo(original.getHotelId());
        assertThat(restored.getDate()).isEqualTo(original.getDate());
        assertThat(restored.getOccupied()).isEqualTo(original.getOccupied());
        assertThat(restored.getCapacity()).isEqualTo(original.getCapacity());
        assertThat(restored.getStatus()).isEqualTo(original.getStatus());
    }
}
