package com.rzodeczko.application.port.in;

import java.util.OptionalLong;

public interface GetHotelCapacityUseCase {
    OptionalLong getCapacity(long hotelId);
}
