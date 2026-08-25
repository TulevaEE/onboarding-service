package ee.tuleva.onboarding.investment.instrument;

import ee.tuleva.onboarding.instrument.InstrumentReferenceChange;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@NullMarked
@RequiredArgsConstructor
class InstrumentReferenceChangeDescriber {

  private final ObjectMapper objectMapper;

  String describe(List<InstrumentReferenceChange> changes) {
    var body = new StringBuilder("Instrument reference data changed:\n\n");
    for (var change : changes) {
      body.append(
          "%s %s by %s at %s\n"
              .formatted(
                  change.operation(), change.isin(), change.changedBy(), change.changedAt()));
      for (var line : fieldLines(change)) {
        body.append("  ").append(line).append('\n');
      }
      body.append('\n');
    }
    return body.toString();
  }

  private List<String> fieldLines(InstrumentReferenceChange change) {
    var before = read(change, change.oldValues());
    var after = read(change, change.newValues());

    if (before != null && after != null) {
      return differences(before, after);
    }
    if (after != null) {
      return presentValues(after);
    }
    if (before != null) {
      return presentValues(before);
    }
    throw new IllegalStateException(
        "Instrument reference history row has neither old nor new values: historyId=%s, isin=%s"
            .formatted(change.id(), change.isin()));
  }

  private static List<String> presentValues(JsonNode node) {
    var lines = new ArrayList<String>();
    for (var fieldName : node.propertyNames()) {
      var value = node.path(fieldName);
      if (!value.isNull()) {
        lines.add("%s: %s".formatted(fieldName, text(value)));
      }
    }
    return lines;
  }

  private static List<String> differences(JsonNode before, JsonNode after) {
    var fieldNames = new LinkedHashSet<>(before.propertyNames());
    fieldNames.addAll(after.propertyNames());

    var lines = new ArrayList<String>();
    for (var fieldName : fieldNames) {
      var oldValue = before.path(fieldName);
      var newValue = after.path(fieldName);
      if (!oldValue.equals(newValue)) {
        lines.add("%s: %s -> %s".formatted(fieldName, text(oldValue), text(newValue)));
      }
    }
    return lines;
  }

  private static String text(JsonNode node) {
    return node.isMissingNode() || node.isNull() ? "null" : node.asString();
  }

  private @Nullable JsonNode read(InstrumentReferenceChange change, @Nullable String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse instrument reference history values: historyId=%s, isin=%s, json=%s"
              .formatted(change.id(), change.isin(), json),
          e);
    }
  }
}
