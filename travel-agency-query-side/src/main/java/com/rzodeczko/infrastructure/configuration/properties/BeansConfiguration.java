package com.rzodeczko.infrastructure.configuration.properties;

import com.rzodeczko.application.port.out.AvailabilityReadRepository;
import com.rzodeczko.application.port.out.AvailabilityWriteRepository;
import com.rzodeczko.application.port.out.HotelCapacityProvider;
import com.rzodeczko.application.port.out.HotelCapacityWriteRepository;
import com.rzodeczko.application.service.AvailabilityService;
import com.rzodeczko.application.service.HotelCapacityService;
import com.rzodeczko.domain.model.AvailabilityStatusPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeansConfiguration {

    @Bean
    public AvailabilityStatusPolicy availabilityStatusPolicy(@Value("${app.last-rooms-threshold:0.9}") double lastRoomsThreshold) {
        return new AvailabilityStatusPolicy(lastRoomsThreshold);
    }

    @Bean
    public AvailabilityService availabilityService(
            AvailabilityWriteRepository writeRepository,
            AvailabilityReadRepository readRepository,
            HotelCapacityProvider hotelCapacityProvider,
            AvailabilityStatusPolicy statusPolicy
    ) {
        return new AvailabilityService(
                writeRepository,
                readRepository,
                hotelCapacityProvider,
                statusPolicy);
    }

    @Bean
    public HotelCapacityService hotelCapacityProvider(
            HotelCapacityWriteRepository capacityWriteRepository,
            AvailabilityReadRepository readRepository,
            AvailabilityWriteRepository writeRepository,
            AvailabilityStatusPolicy availabilityStatusPolicy
    ) {
        return new HotelCapacityService(
                capacityWriteRepository,
                readRepository,
                writeRepository,
                availabilityStatusPolicy);
    }
}
