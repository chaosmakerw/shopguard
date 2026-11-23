package com.codesprint.lesson0831.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelPropertiesValidationTest {

    @Test
    void emptyProperties_should_fail_validation() {
        Validator validator = Validation
                .buildDefaultValidatorFactory()
                .getValidator();

        ModelProperties modelProperties = new ModelProperties();
        Set<ConstraintViolation<ModelProperties>> violations =
                validator.validate(modelProperties);

        assertTrue(violations.size() > 0);
    }

    @Test
    void unresolved_environment_placeholders_should_fail_validation() {
        Validator validator = Validation
                .buildDefaultValidatorFactory()
                .getValidator();

        ModelProperties modelProperties = new ModelProperties();
        modelProperties.setBaseUrl("${MODEL_BASE_URL}");
        modelProperties.setApiKey("${MODEL_API_KEY}");
        modelProperties.setName("${MODEL_NAME}");

        Set<ConstraintViolation<ModelProperties>> violations =
                validator.validate(modelProperties);

        assertTrue(violations.size() >= 3);
    }
}
