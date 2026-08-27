package ee.tuleva.onboarding.analytics;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecurringSavers {

  private static final int LOOKBACK_MONTHS = 4;
  private static final int MIN_CONTRIBUTION_MONTHS = 3;

  private final AnalyticsThirdPillarTransactionRepository thirdPillarTransactions;
  private final SavingFundPaymentRepository savingsFundPayments;
  private final Clock clock;

  public boolean hasRecurringSavingsFundPayments(PartyId party) {
    LocalDate from = LocalDate.now(clock).withDayOfMonth(1).minusMonths(LOOKBACK_MONTHS - 1);
    return savingsFundPayments.countIssuedPaymentMonthsSince(party, from)
        >= MIN_CONTRIBUTION_MONTHS;
  }

  public RecurringPayments recurringPaymentsOf(String personalCode) {
    LocalDate from = LocalDate.now(clock).withDayOfMonth(1).minusMonths(LOOKBACK_MONTHS - 1);
    return new RecurringPayments(
        thirdPillarTransactions.countOwnContributionMonthsSince(personalCode, from)
            >= MIN_CONTRIBUTION_MONTHS,
        savingsFundPayments.countIssuedPaymentMonthsSince(
                new PartyId(PartyId.Type.PERSON, personalCode), from)
            >= MIN_CONTRIBUTION_MONTHS);
  }
}
