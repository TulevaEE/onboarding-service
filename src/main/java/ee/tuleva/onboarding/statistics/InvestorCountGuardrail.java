package ee.tuleva.onboarding.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvestorCountGuardrail {

  private final InvestorCountGuardrailProperties properties;

  public List<String> findViolations(long current, OptionalLong previous) {
    List<String> violations = new ArrayList<>();

    if (current < properties.minCount() || current > properties.maxCount()) {
      violations.add(
          "count out of expected bounds: count="
              + current
              + ", expected=["
              + properties.minCount()
              + ", "
              + properties.maxCount()
              + "]");
    }

    if (previous.isPresent()) {
      long previousCount = previous.getAsLong();
      if (previousCount == 0) {
        if (current != 0) {
          violations.add("count changed from 0 in the previous period to current=" + current);
        }
      } else {
        double deltaPercent = Math.abs(current - previousCount) / (double) previousCount * 100.0;
        if (deltaPercent > properties.maxDeltaPercent()) {
          violations.add(
              String.format(
                  "count changed by %.2f%% vs previous period: previous=%d, current=%d, maxDeltaPercent=%.2f",
                  deltaPercent, previousCount, current, properties.maxDeltaPercent()));
        }
      }
    }

    return violations;
  }
}
