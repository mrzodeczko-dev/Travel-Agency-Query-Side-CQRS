package com.rzodeczko.application.service;

import com.rzodeczko.application.port.out.AvailabilityReadRepository;
import com.rzodeczko.application.port.out.AvailabilityWriteRepository;
import com.rzodeczko.application.port.out.HotelCapacityWriteRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotelCapacityServiceTest {

    @Mock
    private HotelCapacityWriteRepository capacityWriteRepository;
    @Mock
    private AvailabilityReadRepository readRepository;
    @Mock
    private AvailabilityWriteRepository writeRepository;

    private HotelCapacityService service;

    private static final long HOTEL_ID = 1L;

    @BeforeEach
    void setUp() {
        AvailabilityStatusPolicy policy = new AvailabilityStatusPolicy(0.9);
        service = new HotelCapacityService(capacityWriteRepository, readRepository, writeRepository, policy);
    }

    @Test
    void shouldSaveNewCapacity() {
        when(readRepository.findByHotel(HOTEL_ID, null, null)).thenReturn(List.of());

        service.upsert(HOTEL_ID, 200L);

        verify(capacityWriteRepository).save(HOTEL_ID, 200L);
    }

    @Test
    void shouldReprojectExistingDaysWithNewCapacity() {
        LocalDate date = LocalDate.of(2024, 6, 1);
        Availability existing = new Availability(HOTEL_ID, date, 50, 100, AvailabilityStatus.AVAILABLE);
        when(readRepository.findByHotel(HOTEL_ID, null, null)).thenReturn(List.of(existing));

        service.upsert(HOTEL_ID, 200L);

        ArgumentCaptor<Availability> captor = ArgumentCaptor.forClass(Availability.class);
        verify(writeRepository).upsert(captor.capture());

        Availability reprojected = captor.getValue();
        assertThat(reprojected.getCapacity()).isEqualTo(200L);
        assertThat(reprojected.getOccupied()).isEqualTo(50);
        assertThat(reprojected.getHotelId()).isEqualTo(HOTEL_ID);
        assertThat(reprojected.getDate()).isEqualTo(date);
    }

    @Test
    void shouldReprojectStatusBasedOnNewCapacity() {
        LocalDate date = LocalDate.of(2024, 6, 1);
        // occupied=90 przy capacity=100 → LAST_ROOMS, ale przy capacity=200 → AVAILABLE
        Availability existing = new Availability(HOTEL_ID, date, 90, 100, AvailabilityStatus.LAST_ROOMS);
        when(readRepository.findByHotel(HOTEL_ID, null, null)).thenReturn(List.of(existing));

        service.upsert(HOTEL_ID, 200L);

        ArgumentCaptor<Availability> captor = ArgumentCaptor.forClass(Availability.class);
        verify(writeRepository).upsert(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void shouldReprojectAllDaysForHotel() {
        List<Availability> days = List.of(
                new Availability(HOTEL_ID, LocalDate.of(2024, 6, 1), 10, 100, AvailabilityStatus.AVAILABLE),
                new Availability(HOTEL_ID, LocalDate.of(2024, 6, 2), 20, 100, AvailabilityStatus.AVAILABLE),
                new Availability(HOTEL_ID, LocalDate.of(2024, 6, 3), 30, 100, AvailabilityStatus.AVAILABLE)
        );
        when(readRepository.findByHotel(HOTEL_ID, null, null)).thenReturn(days);

        service.upsert(HOTEL_ID, 150L);

        verify(writeRepository, times(3)).upsert(any());
    }

    @Test
    void shouldNotCallWriteRepositoryWhenHotelHasNoDays() {
        when(readRepository.findByHotel(HOTEL_ID, null, null)).thenReturn(List.of());

        service.upsert(HOTEL_ID, 100L);

        verifyNoInteractions(writeRepository);
    }
}
