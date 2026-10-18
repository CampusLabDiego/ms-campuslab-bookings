package cl.campuslab.bookings.stock;

import cl.campuslab.bookings.enums.BookingStatus;

public final class BookingTransitions {

    private BookingTransitions() {
    }

    public static boolean allowed(
            BookingStatus current,
            BookingStatus next
    ) {
        if (current == null || next == null) {
            return false;
        }

        return switch (current) {

            case SOLICITADA ->
                    next == BookingStatus.APROBADA
                    || next == BookingStatus.CANCELADA;

            case APROBADA ->
                    next == BookingStatus.EN_PREPARACION
                    || next == BookingStatus.CANCELADA;

            case EN_PREPARACION ->
                    next == BookingStatus.EN_USO;

            case EN_USO ->
                    next == BookingStatus.DEVUELTA;

            case DEVUELTA, CANCELADA -> false;
        };
    }

    public static boolean active(BookingStatus status) {
        return status == BookingStatus.APROBADA
                || status == BookingStatus.EN_PREPARACION
                || status == BookingStatus.EN_USO;
    }

    public static String stockAction(BookingStatus target) {
        return switch (target) {

            case APROBADA, EN_PREPARACION, EN_USO ->
                    "RESERVE";

            case CANCELADA ->
                    "RELEASE";

            case DEVUELTA ->
                    "FINISH";

            default -> throw new IllegalArgumentException(
                    "Estado sin movimiento de stock."
            );
        };
    }
}