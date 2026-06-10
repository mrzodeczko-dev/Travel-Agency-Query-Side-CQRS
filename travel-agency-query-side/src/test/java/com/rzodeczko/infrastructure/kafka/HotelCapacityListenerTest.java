package com.rzodeczko.infrastructure.kafka;

import com.rzodeczko.application.port.in.UpsertHotelCapacityUseCase;
import com.rzodeczko.avro.HotelUpsertedAvro;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HotelCapacityListenerTest {

    @Mock
    private UpsertHotelCapacityUseCase upsertHotelCapacityUseCase;

    @InjectMocks
    private HotelCapacityListener listener;

    @Test
    void shouldDelegateToUseCaseWithCorrectParameters() {
        HotelUpsertedAvro event = HotelUpsertedAvro.newBuilder()
                .setHotelId(42L)
                .setCapacity(300L)
                .build();

        listener.onHotelUpserted(event);

        verify(upsertHotelCapacityUseCase).upsert(42L, 300L);
    }

    @Test
    void shouldHandleSmallCapacity() {
        HotelUpsertedAvro event = HotelUpsertedAvro.newBuilder()
                .setHotelId(1L)
                .setCapacity(1L)
                .build();

        listener.onHotelUpserted(event);

        verify(upsertHotelCapacityUseCase).upsert(1L, 1L);
    }

    @Test
    void shouldHandleLargeCapacity() {
        HotelUpsertedAvro event = HotelUpsertedAvro.newBuilder()
                .setHotelId(1L)
                .setCapacity(10_000L)
                .build();

        listener.onHotelUpserted(event);

        verify(upsertHotelCapacityUseCase).upsert(1L, 10_000L);
    }
}
