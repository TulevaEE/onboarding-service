package ee.tuleva.onboarding.investment.check.limit;

import java.util.List;
import lombok.Getter;

@Getter
class LimitCheckPartialFailureException extends RuntimeException {

  private final List<LimitCheckResult> partialResults;

  LimitCheckPartialFailureException(String message, List<LimitCheckResult> partialResults) {
    super(message);
    this.partialResults = partialResults;
  }
}
