package ee.tuleva.onboarding.investment.fees.rate;

import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.AGREEMENT;
import static ee.tuleva.onboarding.investment.fees.rate.RateBasis.PUBLISHED_FALLBACK;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.FIXED;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.FIXED_NET;
import static ee.tuleva.onboarding.investment.fees.rate.RebateKind.NONE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.investment.fees.rate.MonthResolution.Failed;
import ee.tuleva.onboarding.investment.fees.rate.MonthResolution.Resolved;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentFeeRateNotifierTest {

  private static final YearMonth AUGUST = YearMonth.of(2026, 8);
  private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

  @Mock private OperationsNotificationService notificationService;

  @Test
  void staysQuietWhenEveryRateCameFromItsAgreementAsAgreed() {
    notifier()
        .announce(
            List.of(
                new Resolved(
                    SEPTEMBER,
                    List.of(rate("ZZ0000000001", "0.00070000", AGREEMENT, null, NONE)))));

    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void postsEveryFallbackAboveNetAndFailedMonthSoTheyCanBeFixedBeforeTheConsumersRead() {
    notifier()
        .announce(
            List.of(
                new Failed(AUGUST, "IllegalStateException"),
                new Resolved(
                    SEPTEMBER,
                    List.of(
                        rate("ZZ0000000001", "0.00070000", AGREEMENT, null, NONE),
                        rate(
                            "ZZ0000000002",
                            "0.00070000",
                            PUBLISHED_FALLBACK,
                            "the tiered agreement needs the month's volume",
                            FIXED),
                        rate("ZZ0000000003", "0.00090000", AGREEMENT, null, FIXED_NET)))));

    then(notificationService)
        .should()
        .sendMessage(
            "❌ Instrument fee rates need a look before the OCF and the TD attribution read them:"
                + " 2026-08, 2026-09\n"
                + "  2026-08 could not be resolved: IllegalStateException\n"
                + "  2026-09 ZZ0000000002 fell back to the published OCF: the tiered agreement"
                + " needs the month's volume\n"
                + "  2026-09 ZZ0000000003 agreed net OCF is above the published OCF; the fund"
                + " bears the agreed net, check the agreement\n"
                + "Once an agreement is fixed, INSERT INTO investment_job_trigger (job_name) VALUES"
                + " ('InstrumentFeeRateJob') resolves every closed month again; the newest rates"
                + " are the ones read.",
            INVESTMENT,
            ERROR);
  }

  private InstrumentFeeRateNotifier notifier() {
    return new InstrumentFeeRateNotifier(notificationService);
  }

  private static InstrumentRate rate(
      String isin,
      String netOcf,
      RateBasis rateBasis,
      @Nullable String fallbackReason,
      RebateKind rebateKind) {
    return new InstrumentRate(
        1L,
        isin,
        SEPTEMBER,
        new BigDecimal("0.00070000"),
        new BigDecimal(netOcf),
        rateBasis,
        fallbackReason,
        rebateKind);
  }
}
