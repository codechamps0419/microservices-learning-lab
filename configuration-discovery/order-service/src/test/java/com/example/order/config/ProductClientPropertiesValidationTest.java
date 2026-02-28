package com.example.order.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class ProductClientPropertiesValidationTest {
 @Test void missingLogicalServiceNameFailsValidation() {
   try (var factory = Validation.buildDefaultValidatorFactory()) {
     var violations = factory.getValidator().validate(new ProductClientProperties("", Duration.ofSeconds(1), Duration.ofSeconds(2)));
     assertThat(violations).isNotEmpty();
   }
 }
}
