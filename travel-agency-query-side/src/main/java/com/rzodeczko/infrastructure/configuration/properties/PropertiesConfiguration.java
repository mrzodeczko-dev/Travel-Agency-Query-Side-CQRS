package com.rzodeczko.infrastructure.configuration.properties;

import com.rzodeczko.infrastructure.capacity.properteis.HotelCapacityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({HotelCapacityProperties.class, AppTopicsProperties.class})
public class PropertiesConfiguration {
}
