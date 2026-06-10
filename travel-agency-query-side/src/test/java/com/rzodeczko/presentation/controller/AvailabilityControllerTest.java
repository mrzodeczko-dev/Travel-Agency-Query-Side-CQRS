package com.rzodeczko.presentation.controller;

import com.rzodeczko.application.port.in.GetAvailabilityUseCase;
import com.rzodeczko.domain.model.Availability;
import com.rzodeczko.domain.model.AvailabilityStatus;
import com.rzodeczko.presentation.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AvailabilityController.class, GlobalExceptionHandler.class})
class AvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetAvailabilityUseCase getAvailabilityUseCase;

    private static final long HOTEL_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2024, 6, 1);

    @Test
    void shouldReturnAvailabilityListForHotel() throws Exception {
        Availability a = new Availability(HOTEL_ID, DATE, 30, 100, AvailabilityStatus.AVAILABLE);
        when(getAvailabilityUseCase.getForHotel(HOTEL_ID, null, null)).thenReturn(List.of(a));

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].hotelId").value(HOTEL_ID))
                .andExpect(jsonPath("$[0].date").value("2024-06-01"))
                .andExpect(jsonPath("$[0].occupied").value(30))
                .andExpect(jsonPath("$[0].capacity").value(100))
                .andExpect(jsonPath("$[0].freeRooms").value(70))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    void shouldPassDateRangeToUseCase() throws Exception {
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate to = LocalDate.of(2024, 6, 7);
        when(getAvailabilityUseCase.getForHotel(HOTEL_ID, from, to)).thenReturn(List.of());

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID)
                        .param("from", "2024-06-01")
                        .param("to", "2024-06-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(getAvailabilityUseCase).getForHotel(HOTEL_ID, from, to);
    }

    @Test
    void shouldReturnEmptyListWhenNoData() throws Exception {
        when(getAvailabilityUseCase.getForHotel(HOTEL_ID, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldReturnBadRequestWhenFromIsAfterTo() throws Exception {
        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID)
                        .param("from", "2024-06-10")
                        .param("to", "2024-06-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid date range")));

        verifyNoInteractions(getAvailabilityUseCase);
    }

    @Test
    void shouldReturnMultipleAvailabilities() throws Exception {
        List<Availability> list = List.of(
                new Availability(HOTEL_ID, DATE, 10, 100, AvailabilityStatus.AVAILABLE),
                new Availability(HOTEL_ID, DATE.plusDays(1), 95, 100, AvailabilityStatus.LAST_ROOMS),
                new Availability(HOTEL_ID, DATE.plusDays(2), 100, 100, AvailabilityStatus.SOLD_OUT)
        );
        when(getAvailabilityUseCase.getForHotel(HOTEL_ID, null, null)).thenReturn(list);

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$[1].status").value("LAST_ROOMS"))
                .andExpect(jsonPath("$[2].status").value("SOLD_OUT"));
    }

    @Test
    void shouldAllowOnlyFromParameter() throws Exception {
        LocalDate from = LocalDate.of(2024, 6, 1);
        when(getAvailabilityUseCase.getForHotel(HOTEL_ID, from, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID)
                        .param("from", "2024-06-01"))
                .andExpect(status().isOk());

        verify(getAvailabilityUseCase).getForHotel(HOTEL_ID, from, null);
    }

    @Test
    void shouldReturn500WhenUseCaseThrowsUnexpectedException() throws Exception {
        when(getAvailabilityUseCase.getForHotel(anyLong(), any(), any()))
                .thenThrow(new RuntimeException("Unexpected"));

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
