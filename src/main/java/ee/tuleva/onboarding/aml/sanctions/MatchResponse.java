package ee.tuleva.onboarding.aml.sanctions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

public record MatchResponse(ArrayNode results, JsonNode query) {}
