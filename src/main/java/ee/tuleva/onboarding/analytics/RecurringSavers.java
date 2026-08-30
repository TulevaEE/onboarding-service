package ee.tuleva.onboarding.analytics;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.mandate.RecurringContributions;
import ee.tuleva.onboarding.mandate.RecurringPayments;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecurringSavers implements RecurringContributions {

  private static final int LOOKBACK_MONTHS = 4;
  private static final int MIN_CONTRIBUTION_MONTHS = 3;

  private final AnalyticsThirdPillarTransactionRepository thirdPillarTransactions;
  private final SavingsFundContributions savingsFundContributions;
  private final Clock clock;

  public boolean hasRecurringSavingsFundPayments(SaverId saver) {
    LocalDate from = LocalDate.now(clock).withDayOfMonth(1).minusMonths(LOOKBACK_MONTHS - 1);
    return savingsFundContributions.countIssuedPaymentMonthsSince(saver, from)
        >= MIN_CONTRIBUTION_MONTHS;
  }

  @Override
  public RecurringPayments recurringPaymentsOf(String personalCode) {
    LocalDate from = LocalDate.now(clock).withDayOfMonth(1).minusMonths(LOOKBACK_MONTHS - 1);
    return new RecurringPayments(
        thirdPillarTransactions.countOwnContributionMonthsSince(personalCode, from)
            >= MIN_CONTRIBUTION_MONTHS,
        savingsFundContributions.countIssuedPaymentMonthsSince(SaverId.person(personalCode), from)
            >= MIN_CONTRIBUTION_MONTHS);
  }
}
