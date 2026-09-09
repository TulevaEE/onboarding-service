package ee.tuleva.onboarding.analytics;

import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.nudge.NudgeAccount;
import ee.tuleva.onboarding.nudge.RecurringContributionStatus;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecurringSavers implements RecurringContributionStatus {

  private static final int LOOKBACK_MONTHS = 4;
  private static final int MIN_CONTRIBUTION_MONTHS = 3;

  private final AnalyticsThirdPillarTransactionRepository thirdPillarTransactions;
  private final SavingsFundContributions savingsFundContributions;
  private final Clock clock;

  @Override
  public boolean thirdPillar(String personalCode) {
    return thirdPillarTransactions.countOwnContributionMonthsSince(personalCode, windowStart())
        >= MIN_CONTRIBUTION_MONTHS;
  }

  @Override
  public boolean savingsFund(NudgeAccount account) {
    return savingsFundContributions.countIssuedPaymentMonthsSince(saverId(account), windowStart())
        >= MIN_CONTRIBUTION_MONTHS;
  }

  private LocalDate windowStart() {
    return LocalDate.now(clock).withDayOfMonth(1).minusMonths(LOOKBACK_MONTHS - 1);
  }

  private static SaverId saverId(NudgeAccount account) {
    return new SaverId(
        switch (account.type()) {
          case PERSON -> SaverId.Type.PERSON;
          case LEGAL_ENTITY -> SaverId.Type.LEGAL_ENTITY;
        },
        account.code());
  }
}
