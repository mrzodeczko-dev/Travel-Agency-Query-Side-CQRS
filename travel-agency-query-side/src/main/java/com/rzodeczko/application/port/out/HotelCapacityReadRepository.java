package com.rzodeczko.application.port.out;

import java.util.OptionalLong;

public interface HotelCapacityReadRepository {
    OptionalLong findCapacity(long hotelId);
}
