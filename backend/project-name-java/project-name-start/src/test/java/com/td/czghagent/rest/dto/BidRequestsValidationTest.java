package com.td.czghagent.rest.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BidRequestsValidationTest {
    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void acceptsModelSubpointSuffixForBusinessNormalization() {
        String criterionId = "f1b7559d-645d-3d18-8776-139bd8e20a93";
        BidRequests.OutlineNode node = outlineNode(List.of(criterionId + "-9"));

        assertThat(validator.validate(node)).isEmpty();
    }

    @Test
    void rejectsUnreasonablyLongScoringReferences() {
        BidRequests.OutlineNode node = outlineNode(List.of("x".repeat(201)));

        assertThat(validator.validate(node))
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .equals("scoringPointIds[0].<list element>"));
    }

    private BidRequests.OutlineNode outlineNode(List<String> scoringPointIds) {
        return new BidRequests.OutlineNode(
                "node-1", null, 1, "Technical solution", 0,
                "", List.of(), scoringPointIds);
    }
}
