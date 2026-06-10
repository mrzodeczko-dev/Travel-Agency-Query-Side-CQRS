package com.rzodeczko.infrastructure.capacity;

import com.rzodeczko.application.port.out.HotelCapacityProvider;
import com.rzodeczko.infrastructure.persistence.document.HotelDocument;
import com.rzodeczko.infrastructure.persistence.repository.MongoHotelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongoHotelCapacityProviderTest {

    @Mock
    private MongoHotelRepository hotelRepository;
    @Mock
    private HotelCapacityProvider fallback;

    private MongoHotelCapacityProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MongoHotelCapacityProvider(hotelRepository, fallback);
    }

    // ── getCapacity ──────────────────────────────────────────────────────────

    @Test
    void shouldReturnCapacityFromMongoWhenHotelExists() {
        HotelDocument doc = HotelDocument.builder().id(1L).capacity(150).build();
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(doc));

        long capacity = provider.getCapacity(1L);

        assertThat(capacity).isEqualTo(150);
        verifyNoInteractions(fallback);
    }

    @Test
    void shouldReturnFallbackCapacityWhenHotelNotInMongo() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.empty());
        when(fallback.getCapacity(1L)).thenReturn(100L);

        long capacity = provider.getCapacity(1L);

        assertThat(capacity).isEqualTo(100);
        verify(fallback).getCapacity(1L);
    }

    @Test
    void shouldCacheCapacityAfterFirstMongoLookup() {
        HotelDocument doc = HotelDocument.builder().id(1L).capacity(200).build();
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(doc));

        provider.getCapacity(1L);
        provider.getCapacity(1L);
        provider.getCapacity(1L);

        // only one DB call — subsequent calls use cache
        verify(hotelRepository, times(1)).findById(1L);
    }

    @Test
    void shouldNotCacheFallbackValue() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.empty());
        when(fallback.getCapacity(1L)).thenReturn(100L);

        provider.getCapacity(1L);
        provider.getCapacity(1L);

        // each call hits the repo again because fallback values are not cached
        verify(hotelRepository, times(2)).findById(1L);
    }

    @Test
    void shouldKeepCacheIsolatedBetweenHotels() {
        when(hotelRepository.findById(1L)).thenReturn(
                Optional.of(HotelDocument.builder().id(1L).capacity(100).build()));
        when(hotelRepository.findById(2L)).thenReturn(
                Optional.of(HotelDocument.builder().id(2L).capacity(200).build()));

        assertThat(provider.getCapacity(1L)).isEqualTo(100);
        assertThat(provider.getCapacity(2L)).isEqualTo(200);
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    void shouldPersistHotelDocumentOnSave() {
        provider.save(1L, 300);

        verify(hotelRepository).save(any(HotelDocument.class));
    }

    @Test
    void shouldUpdateCacheOnSave() {
        // first lookup from DB
        when(hotelRepository.findById(1L)).thenReturn(
                Optional.of(HotelDocument.builder().id(1L).capacity(100).build()));
        assertThat(provider.getCapacity(1L)).isEqualTo(100);

        // save new capacity
        provider.save(1L, 300);

        // cache should return new value without DB call
        assertThat(provider.getCapacity(1L)).isEqualTo(300);
        // only 1 findById call (the initial one), not 2
        verify(hotelRepository, times(1)).findById(1L);
    }
}
