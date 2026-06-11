package com.rzodeczko.application.service;

import com.rzodeczko.application.port.in.GetHotelCapacityUseCase;
import com.rzodeczko.application.port.in.UpsertHotelCapacityUseCase;
import com.rzodeczko.application.port.out.AvailabilityReadRepository;
import com.rzodeczko.application.port.out.AvailabilityWriteRepository;
import com.rzodeczko.application.port.out.HotelCapacityReadRepository;
import com.rzodeczko.application.port.out.HotelCapacityWriteRepository;
import com.rzodeczko.domain.model.AvailabilityStatus;
import com.rzodeczko.domain.model.AvailabilityStatusPolicy;
import com.rzodeczko.domain.model.Availability;

import java.util.OptionalLong;

public class HotelCapacityService implements UpsertHotelCapacityUseCase, GetHotelCapacityUseCase {

    private final HotelCapacityWriteRepository hotelCapacityWriteRepository;
    private final HotelCapacityReadRepository hotelCapacityReadRepository;
    private final AvailabilityReadRepository availabilityRepository;
    private final AvailabilityWriteRepository availabilityWriteRepository;
    private final AvailabilityStatusPolicy availabilityStatusPolicy;

    public HotelCapacityService(
            HotelCapacityWriteRepository hotelCapacityWriteRepository,
            HotelCapacityReadRepository hotelCapacityReadRepository,
            AvailabilityReadRepository availabilityRepository,
            AvailabilityWriteRepository availabilityWriteRepository,
            AvailabilityStatusPolicy availabilityStatusPolicy) {
        this.hotelCapacityWriteRepository = hotelCapacityWriteRepository;
        this.hotelCapacityReadRepository = hotelCapacityReadRepository;
        this.availabilityRepository = availabilityRepository;
        this.availabilityWriteRepository = availabilityWriteRepository;
        this.availabilityStatusPolicy = availabilityStatusPolicy;
    }

    @Override
    public OptionalLong getCapacity(long hotelId) {
        return hotelCapacityReadRepository.findCapacity(hotelId);
    }

    @Override
    public void upsert(long hotelId, long capacity) {
        hotelCapacityWriteRepository.save(hotelId, capacity);
        reprojectHotelDays(hotelId, capacity);
    }

    private void reprojectHotelDays(long hotelId, long capacity) {
        availabilityRepository.forEachByHotel(hotelId, day -> {
            AvailabilityStatus newStatus = availabilityStatusPolicy.evaluate(day.getOccupied(), capacity);
            Availability corrected = new Availability(
                    day.getHotelId(),
                    day.getDate(),
                    day.getOccupied(),
                    capacity,
                    newStatus
            );
            availabilityWriteRepository.upsert(corrected);
        });
    }
}
