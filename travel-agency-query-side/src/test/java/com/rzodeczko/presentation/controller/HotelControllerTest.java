package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.port.in.GetHotelCapacityUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.OptionalLong;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HotelController.class)
class HotelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetHotelCapacityUseCase getHotelCapacityUseCase;

    @Test
    void getHotel_existingHotel_returns200WithCapacity() throws Exception {
        when(getHotelCapacityUseCase.getCapacity(42L)).thenReturn(OptionalLong.of(200));

        mockMvc.perform(get("/api/hotels/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotelId").value(42))
                .andExpect(jsonPath("$.capacity").value(200));
    }

    @Test
    void getHotel_nonExistentHotel_returns404() throws Exception {
        when(getHotelCapacityUseCase.getCapacity(999L)).thenReturn(OptionalLong.empty());

        mockMvc.perform(get("/api/hotels/999"))
                .andExpect(status().isNotFound());
    }
}
