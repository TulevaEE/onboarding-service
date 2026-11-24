package ee.tuleva.onboarding.investment.event;

import java.time.LocalDate;

public record RunInstrumentFeeAccrualBackfillRequested(LocalDate firstDay) {

  public static final String JOB_NAME = "InstrumentFeeAccrualBackfillJob";
}
