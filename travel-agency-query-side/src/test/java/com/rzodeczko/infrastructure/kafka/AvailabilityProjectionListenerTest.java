package com.rzodeczko.infrastructure.kafka;

import com.rzodeczko.application.command.UpdateAvailabilityCommand;
import com.rzodeczko.application.port.in.UpdateAvailabilityUseCase;
import com.rzodeczko.avro.AvailabilityUpdatedAvro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AvailabilityProjectionListenerTest {

    @Mock
    private UpdateAvailabilityUseCase updateAvailabilityUseCase;

    @InjectMocks
    private AvailabilityProjectionListener listener;

    @Test
    void shouldConvertAvroEventToCommandAndCallUseCase() {
        AvailabilityUpdatedAvro event = AvailabilityUpdatedAvro.newBuilder()
                .setHotelId(1L)
                .setDate("2024-06-01")
                .setOccupied(50L)
                .build();

        listener.onAvailabilityUpdated(event);

        ArgumentCaptor<UpdateAvailabilityCommand> captor =
                ArgumentCaptor.forClass(UpdateAvailabilityCommand.class);
        verify(updateAvailabilityUseCase).update(captor.capture());

        UpdateAvailabilityCommand command = captor.getValue();
        assertThat(command.hotelId()).isEqualTo(1L);
        assertThat(command.date()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(command.occupied()).isEqualTo(50L);
    }

    @Test
    void shouldParseVariousDateFormats() {
        AvailabilityUpdatedAvro event = AvailabilityUpdatedAvro.newBuilder()
                .setHotelId(99L)
                .setDate("2025-12-31")
                .setOccupied(0L)
                .build();

        listener.onAvailabilityUpdated(event);

        ArgumentCaptor<UpdateAvailabilityCommand> captor =
                ArgumentCaptor.forClass(UpdateAvailabilityCommand.class);
        verify(updateAvailabilityUseCase).update(captor.capture());

        assertThat(captor.getValue().date()).isEqualTo(LocalDate.of(2025, 12, 31));
    }
}
