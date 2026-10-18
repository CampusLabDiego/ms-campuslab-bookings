package cl.campuslab.bookings.stock;

import java.time.LocalDateTime;

public final class ArrivalPolicy {

    private static final long MINUTES_BEFORE_START = 15;

    private ArrivalPolicy() {
    }

    public static void validate(
            LocalDateTime start,
            LocalDateTime end,
            LocalDateTime now
    ) {
        if (
                start == null
                || end == null
                || now == null
                || !end.isAfter(start)
        ) {
            throw new IllegalArgumentException(
                    "El horario de la reserva no es válido."
            );
        }

        LocalDateTime earliestArrival =
                start.minusMinutes(MINUTES_BEFORE_START);

        if (now.isBefore(earliestArrival)) {
            throw new IllegalArgumentException(
                    "Puedes confirmar tu llegada desde "
                    + MINUTES_BEFORE_START
                    + " minutos antes del inicio de la reserva."
            );
        }

        if (!now.isBefore(end)) {
            throw new IllegalArgumentException(
                    "El horario de la reserva ya finalizó."
            );
        }
    }
}