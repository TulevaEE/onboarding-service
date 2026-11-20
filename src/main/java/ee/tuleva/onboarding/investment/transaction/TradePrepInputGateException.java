package ee.tuleva.onboarding.investment.transaction;

import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.investment.transaction.CommandOutcome.StoppedByInputGates;

class TradePrepInputGateException extends RuntimeException {

  private final StoppedByInputGates stop;

  TradePrepInputGateException(StoppedByInputGates stop) {
    super(
        "Trade prep inputs missing or stale, no DRAFT made: fund=%s, asOfDate=%s: %s"
            .formatted(
                stop.fund(),
                stop.asOfDate(),
                stop.failures().stream().map(InputGateFailure::slackLine).collect(joining("; "))));
    this.stop = stop;
  }

  StoppedByInputGates stop() {
    return stop;
  }
}
