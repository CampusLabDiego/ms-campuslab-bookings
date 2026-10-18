package cl.campuslab.bookings.stock;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Representa un rechazo explícito de Catálogo
 * que no aplicó el movimiento solicitado.
 */
public class CatalogRejectedException
        extends ResponseStatusException {

    public CatalogRejectedException(String message) {
        super(
                HttpStatus.CONFLICT,
                message
        );
    }
}