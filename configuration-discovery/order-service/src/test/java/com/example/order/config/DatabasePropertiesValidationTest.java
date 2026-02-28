package com.example.order.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DatabasePropertiesValidationTest {
 @Test void missingMandatoryDatabaseConfigurationFailsValidation() {
   try (var factory = Validation.buildDefaultValidatorFactory()) {
     var violations = factory.getValidator().validate(new DatabaseProperties("", "", ""));
     assertThat(violations).hasSize(3);
   }
 }
}
