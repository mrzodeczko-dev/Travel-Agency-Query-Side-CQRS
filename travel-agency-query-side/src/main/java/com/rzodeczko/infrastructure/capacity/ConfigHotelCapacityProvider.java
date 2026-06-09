package com.rzodeczko.infrastructure.capacity;

import com.rzodeczko.application.port.out.HotelCapacityProvider;
import com.rzodeczko.infrastructure.capacity.properties.HotelCapacityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Qualifier("configHotelCapacityProvider")
public class ConfigHotelCapacityProvider implements HotelCapacityProvider {
    private final HotelCapacityProperties properties;

    @Override
    public long getCapacity(long hotelId) {
        if (properties.overrides() != null && properties.overrides().containsKey(hotelId)) {
            return properties.overrides().get(hotelId);
        }
        return properties.defaultCapacity();
    }
}
