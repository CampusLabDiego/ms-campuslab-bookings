package cl.campuslab.bookings.stock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class CatalogClient {

    private final RestClient http;

    public CatalogClient(
            @Value("${campuslab.catalog-url}")
            String baseUrl
    ) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(client);

        factory.setReadTimeout(Duration.ofSeconds(12));

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public ResourceView resource(
            Long id,
            Jwt jwt
    ) {
        try {
            ResourceView resource = http.get()
                    .uri(
                            "/api/catalog/resources/{id}",
                            id
                    )
                    .headers(headers ->
                            headers.setBearerAuth(
                                    jwt.getTokenValue()
                            )
                    )
                    .retrieve()
                    .body(ResourceView.class);

            if (resource == null) {
                throw unavailable();
            }

            return resource;

        } catch (RestClientResponseException error) {

            if (error.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El recurso ya no existe en el catálogo."
                );
            }

            throw unavailable();

        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    public void apply(
            BookingStockWorkflow.PendingChange change,
            Jwt jwt
    ) {
        try {
            http.put()
                    .uri(
                            "/internal/stock/bookings/{id}",
                            change.bookingId()
                    )
                    .headers(headers ->
                            headers.setBearerAuth(
                                    jwt.getTokenValue()
                            )
                    )
                    .body(new Command(
                            change.resourceId(),
                            change.quantity(),
                            change.action()
                    ))
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientResponseException error) {

            int status = error.getStatusCode().value();

            if (
                    status == 400
                    || status == 404
                    || status == 409
            ) {
                String message =
                        "Catálogo rechazó la operación. "
                        + "Revisa stock, recurso y estado de la reserva.";

                try {
                    CatalogError body =
                            error.getResponseBodyAs(
                                    CatalogError.class
                            );

                    if (
                            body != null
                            && body.message() != null
                            && !body.message().isBlank()
                    ) {
                        message = body.message();
                    }

                } catch (RuntimeException ignored) {
                    // Conserva el mensaje predeterminado.
                }

                throw new CatalogRejectedException(message);
            }

            throw unavailable();

        } catch (RestClientException error) {
            // El movimiento podría haberse confirmado en Catálogo.
            // Se conserva la intención pendiente para reintentar.
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo confirmar la operación con Catálogo. "
                + "Reintenta la MISMA acción antes de cambiar "
                + "a otro estado."
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResourceView(
            Long id,
            String name,
            String type,
            Boolean active,
            Integer availableStock
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogError(
            String message
    ) {
    }

    public record Command(
            Long resourceId,
            int quantity,
            String action
    ) {
    }
}