package ee.tuleva.onboarding.investment.transaction;

import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;

sealed interface CommandOutcome
    permits ProcessCommandResult, CommandOutcome.StoppedByInputGates, CommandOutcome.Failed {

  default List<TransactionOrder> orders() {
    return List.of();
  }

  record StoppedByInputGates(TulevaFund fund, LocalDate asOfDate, List<InputGateFailure> failures)
      implements CommandOutcome {}

  record Failed() implements CommandOutcome {}
}
