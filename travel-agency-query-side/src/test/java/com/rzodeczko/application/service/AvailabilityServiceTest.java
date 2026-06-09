package com.rzodeczko.application.service;

import com.rzodeczko.application.command.UpdateAvailabilityCommand;
import com.rzodeczko.application.port.out.AvailabilityReadRepository;
import com.rzodeczko.application.port.out.AvailabilityWriteRepository;
import com.rzodeczko.application.port.out.HotelCapacityProvider;
import com.rzodeczko.domain.model.Availability;
import com.rzodeczko.domain.model.AvailabilityStatus;
import com.rzodeczko.domain.model.AvailabilityStatusPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @Mock
    private AvailabilityWriteRepository writeRepository;
    @Mock
    private AvailabilityReadRepository readRepository;
    @Mock
    private HotelCapacityProvider capacityProvider;

    private AvailabilityService service;

    private static final long HOTEL_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2024, 6, 1);

    @BeforeEach
    void setUp() {
        AvailabilityStatusPolicy policy = new AvailabilityStatusPolicy(0.9);
        service = new AvailabilityService(writeRepository, readRepository, capacityProvider, policy);
    }


    @Test
    void shouldUpsertAvailabilityWithCorrectCapacityAndStatus() {
        when(capacityProvider.getCapacity(HOTEL_ID)).thenReturn(100L);

        service.update(new UpdateAvailabilityCommand(HOTEL_ID, DATE, 50));

        ArgumentCaptor<Availability> captor = ArgumentCaptor.forClass(Availability.class);
        verify(writeRepository).upsert(captor.capture());

        Availability saved = captor.getValue();
        assertThat(saved.getHotelId()).isEqualTo(HOTEL_ID);
        assertThat(saved.getDate()).isEqualTo(DATE);
        assertThat(saved.getOccupied()).isEqualTo(50);
        assertThat(saved.getCapacity()).isEqualTo(100);
        assertThat(saved.getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void shouldSetLastRoomsStatusWhenOccupancyAboveThreshold() {
        when(capacityProvider.getCapacity(HOTEL_ID)).thenReturn(100L);

        service.update(new UpdateAvailabilityCommand(HOTEL_ID, DATE, 95));

        ArgumentCaptor<Availability> captor = ArgumentCaptor.forClass(Availability.class);
        verify(writeRepository).upsert(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo(AvailabilityStatus.LAST_ROOMS);
    }

    @Test
    void shouldSetSoldOutStatusWhenFullyOccupied() {
        when(capacityProvider.getCapacity(HOTEL_ID)).thenReturn(100L);

        service.update(new UpdateAvailabilityCommand(HOTEL_ID, DATE, 100));

        ArgumentCaptor<Availability> captor = ArgumentCaptor.forClass(Availability.class);
        verify(writeRepository).upsert(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo(AvailabilityStatus.SOLD_OUT);
    }

    @Test
    void shouldFetchCapacityByHotelId() {
        when(capacityProvider.getCapacity(HOTEL_ID)).thenReturn(100L);

        service.update(new UpdateAvailabilityCommand(HOTEL_ID, DATE, 10));

        verify(capacityProvider).getCapacity(HOTEL_ID);
    }


    @Test
    void shouldReturnAvailabilityFromRepository() {
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate to = LocalDate.of(2024, 6, 7);

        List<Availability> expected = List.of(
                new Availability(HOTEL_ID, from, 10, 100, AvailabilityStatus.AVAILABLE),
                new Availability(HOTEL_ID, from.plusDays(1), 90, 100, AvailabilityStatus.LAST_ROOMS)
        );
        when(readRepository.findByHotel(HOTEL_ID, from, to)).thenReturn(expected);

        List<Availability> result = service.getForHotel(HOTEL_ID, from, to);

        assertThat(result).hasSize(2).isEqualTo(expected);
    }

    @Test
    void shouldReturnEmptyListWhenNoAvailabilityFound() {
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate to = LocalDate.of(2024, 6, 7);

        when(readRepository.findByHotel(HOTEL_ID, from, to)).thenReturn(List.of());

        List<Availability> result = service.getForHotel(HOTEL_ID, from, to);

        assertThat(result).isEmpty();
    }
}
