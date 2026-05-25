package ee.tuleva.onboarding.statistics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("investor-count.guardrail")
public record InvestorCountGuardrailProperties(
    long minCount, long maxCount, double maxDeltaPercent) {

  public InvestorCountGuardrailProperties {
    if (minCount < 0 || maxCount < minCount) {
      throw new IllegalStateException(
          "Invalid investor-count guardrail bounds: minCount="
              + minCount
              + ", maxCount="
              + maxCount);
    }
    if (maxDeltaPercent <= 0) {
      throw new IllegalStateException(
          "Invalid investor-count guardrail maxDeltaPercent: " + maxDeltaPercent);
    }
  }
}
