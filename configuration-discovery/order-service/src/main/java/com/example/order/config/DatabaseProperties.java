package com.example.order.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.database")
public record DatabaseProperties(@NotBlank String url, @NotBlank String username, @NotBlank String password) {}
