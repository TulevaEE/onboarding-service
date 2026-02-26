package ee.tuleva.onboarding.aml.sanctions;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

public record MatchResponse(ArrayNode results, JsonNode query) {}
