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
    void shouldReturnPagedAvailabilityForHotel() throws Exception {
        Availability a = new Availability(HOTEL_ID, DATE, 30, 100, AvailabilityStatus.AVAILABLE);
        when(getAvailabilityUseCase.getForHotel(HOTEL_ID, null, null, 0, 30)).thenReturn(List.of(a));
        when(getAvailabilityUseCase.countForHotel(HOTEL_ID, null, null)).thenReturn(1L);

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].hotelId").value(HOTEL_ID))
                .andExpect(jsonPath("$.content[0].date").value("2024-06-01"))
                .andExpect(jsonPath("$.content[0].occupied").value(30))
                .andExpect(jsonPath("$.content[0].capacity").value(100))
                .andExpect(jsonPath("$.content[0].freeRooms").value(70))
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(30))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void shouldPassDateRangeAndPaginationToUseCase() throws Exception {
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate to = LocalDate.of(2024, 6, 7);
        when(getAvailabilityUseCase.getForHotel(HOTEL_ID, from, to, 0, 30)).thenReturn(List.of());
        when(getAvailabilityUseCase.countForHotel(HOTEL_ID, from, to)).thenReturn(0L);

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID)
                        .param("from", "2024-06-01")
                        .param("to", "2024-06-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        verify(getAvailabilityUseCase).getForHotel(HOTEL_ID, from, to, 0, 30);
    }

    @Test
    void shouldReturnEmptyContentWhenNoData() throws Exception {
        when(getAvailabilityUseCase.getForHotel(HOTEL_ID, null, null, 0, 30)).thenReturn(List.of());
        when(getAvailabilityUseCase.countForHotel(HOTEL_ID, null, null)).thenReturn(0L);

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
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
        when(getAvailabilityUseCase.getForHotel(HOTEL_ID, null, null, 0, 30)).thenReturn(list);
        when(getAvailabilityUseCase.countForHotel(HOTEL_ID, null, null)).thenReturn(3L);

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[1].status").value("LAST_ROOMS"))
                .andExpect(jsonPath("$.content[2].status").value("SOLD_OUT"));
    }

    @Test
    void shouldAcceptCustomPageAndSize() throws Exception {
        when(getAvailabilityUseCase.getForHotel(HOTEL_ID, null, null, 2, 10)).thenReturn(List.of());
        when(getAvailabilityUseCase.countForHotel(HOTEL_ID, null, null)).thenReturn(25L);

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID)
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(3));

        verify(getAvailabilityUseCase).getForHotel(HOTEL_ID, null, null, 2, 10);
    }

    @Test
    void shouldReturnBadRequestWhenSizeExceeds100() throws Exception {
        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(getAvailabilityUseCase);
    }

    @Test
    void shouldReturnBadRequestWhenSizeIsZero() throws Exception {
        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID)
                        .param("size", "0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(getAvailabilityUseCase);
    }

    @Test
    void shouldReturnBadRequestWhenPageIsNegative() throws Exception {
        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(getAvailabilityUseCase);
    }

    @Test
    void shouldAcceptMaxAllowedSize() throws Exception {
        when(getAvailabilityUseCase.getForHotel(HOTEL_ID, null, null, 0, 100)).thenReturn(List.of());
        when(getAvailabilityUseCase.countForHotel(HOTEL_ID, null, null)).thenReturn(0L);

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void shouldAllowOnlyFromParameter() throws Exception {
        LocalDate from = LocalDate.of(2024, 6, 1);
        when(getAvailabilityUseCase.getForHotel(HOTEL_ID, from, null, 0, 30)).thenReturn(List.of());
        when(getAvailabilityUseCase.countForHotel(HOTEL_ID, from, null)).thenReturn(0L);

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID)
                        .param("from", "2024-06-01"))
                .andExpect(status().isOk());

        verify(getAvailabilityUseCase).getForHotel(HOTEL_ID, from, null, 0, 30);
    }

    @Test
    void shouldReturn500WhenUseCaseThrowsUnexpectedException() throws Exception {
        when(getAvailabilityUseCase.getForHotel(anyLong(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("Unexpected"));

        mockMvc.perform(get("/api/availability/{hotelId}", HOTEL_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
