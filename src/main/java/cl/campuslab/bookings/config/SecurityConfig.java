package cl.campuslab.bookings.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter authenticationConverter
    ) throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        )
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/bookings",
                                "/api/bookings/**"
                        )
                        .hasAnyRole(
                                "Admin",
                                "Operador",
                                "Cliente"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/bookings"
                        )
                        .hasAnyRole(
                                "Admin",
                                "Operador",
                                "Cliente"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/bookings/*/arrival"
                        )
                        .hasRole("Cliente")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/bookings/*/status"
                        )
                        .hasAnyRole(
                                "Admin",
                                "Operador"
                        )

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/bookings/*/stock/reconcile"
                        )
                        .hasAnyRole(
                                "Admin",
                                "Operador"
                        )

                        .anyRequest()
                        .denyAll()
                )

                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        authenticationConverter
                                )
                        )
                )

                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter rolesConverter =
                new JwtGrantedAuthoritiesConverter();

        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                rolesConverter
        );

        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
            String issuer,

            @Value("${campuslab.security.audience}")
            String audience
    ) {

        NimbusJwtDecoder decoder =
                NimbusJwtDecoder
                        .withIssuerLocation(issuer)
                        .build();

        OAuth2TokenValidator<Jwt> applicationValidator = jwt -> {

            String scope = jwt.getClaimAsString("scp");
            String ownerId = jwt.getClaimAsString("oid");

            boolean validAudience =
                    jwt.getAudience().contains(audience);

            boolean validScope =
                    scope != null
                    && Arrays.stream(scope.split("\\s+"))
                            .anyMatch(
                                    "access_as_user"::equals
                            );

            boolean validOwner =
                    ownerId != null
                    && !ownerId.isBlank();

            boolean hasExpiration =
                    jwt.getExpiresAt() != null;

            if (
                    validAudience
                    && validScope
                    && validOwner
                    && hasExpiration
            ) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            "invalid_token",
                            "El token no cumple los requisitos de CampusLab.",
                            null
                    )
            );
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(
                                issuer
                        ),
                        applicationValidator
                )
        );

        return decoder;
    }
}