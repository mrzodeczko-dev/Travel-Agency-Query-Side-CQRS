package com.rzodeczko.presentation.exception;

import java.time.LocalDate;

public class InvalidDateRangeException extends RuntimeException {
    public InvalidDateRangeException(LocalDate from, LocalDate to) {
        super("Invalid date range: from=" + from + " is after to=" + to);
    }
}
