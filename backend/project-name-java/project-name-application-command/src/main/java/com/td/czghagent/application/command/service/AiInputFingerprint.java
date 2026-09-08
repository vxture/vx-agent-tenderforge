// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-08-06
package com.td.czghagent.application.command.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.td.czghagent.domain.model.BidProductionRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AiInputFingerprint {
    private static final Set<String> TRANSIENT_FIELDS = Set.of(
            "requestid", "traceid", "idempotencykey", "workflowrunid",
            "timestamp", "requestedat", "startedat"
    );
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    String hash(Object value, String promptVersion) {
        try {
            JsonNode tree = objectMapper.valueToTree(value);
            JsonNode canonical = canonicalize(tree);
            return BidProductionRules.generationSnapshotHash(
                    objectMapper.writeValueAsString(canonical), promptVersion);
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            throw new IllegalStateException("AI input cannot be fingerprinted", exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node instanceof ObjectNode object) {
            ObjectNode canonical = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            names.stream().sorted().forEach(name -> {
                if (!TRANSIENT_FIELDS.contains(name.toLowerCase(Locale.ROOT))) {
                    canonical.set(name, canonicalize(object.get(name)));
                }
            });
            return canonical;
        } else if (node instanceof ArrayNode array) {
            ArrayNode canonical = objectMapper.createArrayNode();
            array.elements().forEachRemaining(item -> canonical.add(canonicalize(item)));
            return canonical;
        }
        return node;
    }
}
