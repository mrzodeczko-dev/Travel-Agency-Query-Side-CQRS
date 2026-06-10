package com.rzodeczko.infrastructure.capacity;

import com.rzodeczko.infrastructure.capacity.properties.HotelCapacityProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigHotelCapacityProviderTest {

    @Test
    void shouldReturnDefaultCapacityWhenNoOverrides() {
        var properties = new HotelCapacityProperties(100L, null);
        var provider = new ConfigHotelCapacityProvider(properties);

        assertThat(provider.getCapacity(1L)).isEqualTo(100);
        assertThat(provider.getCapacity(999L)).isEqualTo(100);
    }

    @Test
    void shouldReturnOverrideWhenPresent() {
        var properties = new HotelCapacityProperties(100L, Map.of(1L, 50L, 2L, 200L));
        var provider = new ConfigHotelCapacityProvider(properties);

        assertThat(provider.getCapacity(1L)).isEqualTo(50);
        assertThat(provider.getCapacity(2L)).isEqualTo(200);
    }

    @Test
    void shouldReturnDefaultWhenHotelNotInOverrides() {
        var properties = new HotelCapacityProperties(100L, Map.of(1L, 50L));
        var provider = new ConfigHotelCapacityProvider(properties);

        assertThat(provider.getCapacity(999L)).isEqualTo(100);
    }

    @Test
    void shouldReturnDefaultWhenOverridesMapIsEmpty() {
        var properties = new HotelCapacityProperties(100L, Map.of());
        var provider = new ConfigHotelCapacityProvider(properties);

        assertThat(provider.getCapacity(1L)).isEqualTo(100);
    }
}
