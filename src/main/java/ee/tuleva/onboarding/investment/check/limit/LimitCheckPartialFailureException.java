package ee.tuleva.onboarding.investment.check.limit;

import lombok.Getter;

@Getter
class LimitCheckPartialFailureException extends RuntimeException {

  private final LimitCheckRun partialRun;

  LimitCheckPartialFailureException(String message, LimitCheckRun partialRun) {
    super(message);
    this.partialRun = partialRun;
  }
}
