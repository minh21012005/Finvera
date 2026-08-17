package com.minhnb.finvera_be.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("finvera.security.owner")
public record OwnerProperties(
        @NotNull UUID id,
        @NotBlank String username,
        @NotBlank String passwordHash) {
}
