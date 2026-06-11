package com.rzodeczko.infrastructure.capacity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.rzodeczko.application.port.out.HotelCapacityProvider;
import com.rzodeczko.application.port.out.HotelCapacityReadRepository;
import com.rzodeczko.application.port.out.HotelCapacityWriteRepository;
import com.rzodeczko.infrastructure.persistence.document.HotelDocument;
import com.rzodeczko.infrastructure.persistence.repository.MongoHotelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.OptionalLong;


@Component
@Primary
@Slf4j
public class MongoHotelCapacityProvider implements HotelCapacityProvider, HotelCapacityReadRepository, HotelCapacityWriteRepository {
    private final MongoHotelRepository hotelRepository;
    private final HotelCapacityProvider fallback;

    private final Cache<Long, Long> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    public MongoHotelCapacityProvider(
            MongoHotelRepository hotelRepository,
            @Qualifier("configHotelCapacityProvider") HotelCapacityProvider fallback) {
        this.hotelRepository = hotelRepository;
        this.fallback = fallback;
    }

    @Override
    public long getCapacity(long hotelId) {
        Long cached = cache.getIfPresent(hotelId);
        if (cached != null) {
            return cached;
        }

        return hotelRepository.findById(hotelId)
                .map(doc -> {
                    cache.put(hotelId, doc.getCapacity());
                    return doc.getCapacity();
                })
                .orElseGet(() -> {
                    long fallbackCapacity = fallback.getCapacity(hotelId);
                    log.warn(
                            "No capacity for hotelId={} in Read Model yet. Using fallback: {}. " +
                                    "Ensure hotel is created via POST /api/hotels before accepting bookings.",
                            hotelId, fallbackCapacity
                    );
                    return fallbackCapacity;
                });
    }

    @Override
    public OptionalLong findCapacity(long hotelId) {
        return hotelRepository.findById(hotelId)
                .map(doc -> OptionalLong.of(doc.getCapacity()))
                .orElse(OptionalLong.empty());
    }

    @Override
    public void save(long hotelId, long capacity) {
        hotelRepository.save(HotelDocument.builder().id(hotelId).capacity(capacity).build());
        cache.put(hotelId, capacity);
    }
}
