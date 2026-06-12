package ee.tuleva.onboarding.investment.epis;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.investment.epis.R16Phase.ACTIVE;
import static ee.tuleva.onboarding.investment.epis.R16Phase.IGNORE;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class R16FlowRecalcJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final LocalDate TODAY = LocalDate.of(2026, 6, 11);

  private final PublicHolidays publicHolidays = mock(PublicHolidays.class);
  private final R16StatusService statusService = mock(R16StatusService.class);
  private final OperationsNotificationService notificationService =
      mock(OperationsNotificationService.class);
  private final Clock clock = Clock.fixed(TODAY.atStartOfDay(TALLINN).toInstant(), TALLINN);

  private final R16FlowRecalcJob job =
      new R16FlowRecalcJob(clock, publicHolidays, statusService, notificationService);

  @Test
  void skipsOnNonWorkingDay() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(false);

    job.run();

    verifyNoInteractions(statusService, notificationService);
  }

  @Test
  void skipsNotificationWhenNoFundHasR16Data() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    given(statusService.status())
        .willReturn(
            List.of(emptyStatus(TUK75), emptyStatus(TUK00), emptyStatus(TulevaFund.TUV100)));

    job.run();

    verifyNoInteractions(notificationService);
  }

  @Test
  void sendsSummaryWithPhaseOutflowAndSuppression() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(true);
    given(statusService.status())
        .willReturn(
            List.of(
                new R16FundStatus(
                    TUK75,
                    ACTIVE,
                    new BigDecimal("1000"),
                    new BigDecimal("500"),
                    new BigDecimal("1200.00"),
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 6, 15),
                    LocalDate.of(2026, 6, 8),
                    false),
                emptyStatus(TUK00),
                new R16FundStatus(
                    TUV100,
                    IGNORE,
                    new BigDecimal("100"),
                    new BigDecimal("0"),
                    new BigDecimal("90.00"),
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 6, 15),
                    LocalDate.of(2026, 6, 8),
                    true)));

    job.run();

    verify(notificationService)
        .sendMessage(
            "📤 R16 prognoositud pensionimaksed – 2026-06-11\n"
                + "TUK75: faas ACTIVE, väljamaks 1200.00 €, tähtaeg 2026-06-15,"
                + " müügi tähtaeg 2026-06-08\n"
                + "TUV100: faas IGNORE, väljamaks 90.00 €, tähtaeg 2026-06-15,"
                + " müügi tähtaeg 2026-06-08 (asendatud R45-ga)",
            INVESTMENT);
  }

  @Test
  void runsOnRequestedEvent() {
    given(publicHolidays.isWorkingDay(TODAY)).willReturn(false);

    job.onR16FlowRecalcRequested();

    verifyNoInteractions(statusService, notificationService);
  }

  private static R16FundStatus emptyStatus(TulevaFund fund) {
    return new R16FundStatus(fund, IGNORE, null, null, null, null, null, null, false);
  }
}
