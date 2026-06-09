package com.rzodeczko.infrastructure.capacity;

import com.rzodeczko.application.port.out.HotelCapacityProvider;
import com.rzodeczko.application.port.out.HotelCapacityWriteRepository;
import com.rzodeczko.infrastructure.persistence.document.HotelDocument;
import com.rzodeczko.infrastructure.persistence.repository.MongoHotelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@Component
@Primary
@Slf4j
public class MongoHotelCapacityProvider implements HotelCapacityProvider, HotelCapacityWriteRepository {
    private final MongoHotelRepository hotelRepository;
    private final HotelCapacityProvider fallback;

    private final ConcurrentMap<Long, Long> cache = new ConcurrentHashMap<>();

    public MongoHotelCapacityProvider(
            MongoHotelRepository hotelRepository,
            @Qualifier("configHotelCapacityProvider") HotelCapacityProvider fallback) {
        this.hotelRepository = hotelRepository;
        this.fallback = fallback;
    }

    @Override
    public long getCapacity(long hotelId) {
        Long cached = cache.get(hotelId);
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
    public void save(long hotelId, long capacity) {
        hotelRepository.save(HotelDocument.builder().id(hotelId).capacity(capacity).build());
        cache.put(hotelId, capacity);
    }
}
