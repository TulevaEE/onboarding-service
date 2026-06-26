package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.investment.check.tracking.BenchmarkModelTrackingDifference;
import ee.tuleva.onboarding.investment.check.tracking.TrackingDifferenceQueryService;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.fund.nav.NavCalculationResult.SecurityDetail;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavNotifierTest {

  @Mock private OperationsNotificationService notificationService;
  @Mock private FundNavQueryService fundNavQueryService;
  @Mock private TrackingDifferenceQueryService trackingDifferenceQueryService;
  private final PublicHolidays publicHolidays = new PublicHolidays();

  private NavNotifier navNotifier;

  @BeforeEach
  void setUp() {
    navNotifier =
        new NavNotifier(
            notificationService,
            publicHolidays,
            fundNavQueryService,
            trackingDifferenceQueryService);
  }

  @Test
  void formatMessage_includesAllComponentsAndSecuritiesDetail() {
    var result = aNavCalculationResult();

    var message = navNotifier.formatMessage(result, Optional.empty(), Optional.empty());

    assertThat(message)
        .contains("TKF100")
        .contains("NAV Date: 2026-02-17")
        .contains("Price Date: 2026-02-17")
        .contains("Calculated At: 2026-02-18 13:30:00")
        .contains("6,388,454.59")
        .contains("504,842.47")
        .contains("1,234.56")
        .contains("25,678.90")
        .contains("3,456.78")
        .contains("397.26")
        .contains("6.85")
        .contains("91,782.00")
        .contains("BlackRock Adj", "1,234.00")
        .contains("6,921,444.52")
        .contains("95,642.89")
        .contains("6,824,567.63")
        .contains("693214.12345")
        .contains("*NAV/Unit: 9.8440*")
        .contains("✅", "IE00BMDBMY19", "ESGM.XETRA", "13288", "43.38", "576,433.44", "2026-02-17")
        .contains("❌", "IE00BJZ2DC62", "XRSM.XETRA", "21180", "49.55", "1,049,469.00", "2026-02-12")
        .contains("Tracking Difference (BENCHMARK_MODEL): n/a");
  }

  @Test
  void formatMessage_includesNavChangeAndTrackingDifferenceWithGreenFlag() {
    var result = aNavCalculationResult();

    var message =
        navNotifier.formatMessage(
            result,
            Optional.of(new BigDecimal("-0.014095")),
            Optional.of(
                new BenchmarkModelTrackingDifference(
                    new BigDecimal("-0.000751"), new BigDecimal("0.001"))));

    assertThat(message)
        .contains("*NAV/Unit: 9.8440 (-1.41%)*")
        .contains("Tracking Difference (BENCHMARK_MODEL): -0.08% ✅");
  }

  @Test
  void formatMessage_showsRedFlagWhenTrackingDifferenceBreaches() {
    var result = aNavCalculationResult();

    var message =
        navNotifier.formatMessage(
            result,
            Optional.of(new BigDecimal("0.000245")),
            Optional.of(
                new BenchmarkModelTrackingDifference(
                    new BigDecimal("0.001073"), new BigDecimal("0.001"))));

    assertThat(message)
        .contains("*NAV/Unit: 9.8440 (+0.02%)*")
        .contains("Tracking Difference (BENCHMARK_MODEL): +0.11% 🔴");
  }

  @Test
  void notify_sendsMessageToSavingsChannel() {
    var result = aNavCalculationResult();

    navNotifier.notify(result);

    verify(notificationService).sendMessage(contains("TKF100"), eq(SAVINGS));
  }

  @Test
  void notify_doesNotPropagateExceptions() {
    var result = aNavCalculationResult();
    doThrow(new RuntimeException("Slack down")).when(notificationService).sendMessage(any(), any());

    assertThatCode(() -> navNotifier.notify(result)).doesNotThrowAnyException();
  }

  @Test
  void notify_rendersDayChangeAndTrackingDifferenceFromServices() {
    var result = aNavCalculationResult();
    var previousNavDate = publicHolidays.previousWorkingDay(result.positionReportDate());
    given(fundNavQueryService.findNavPerUnit("TKF100", previousNavDate))
        .willReturn(Optional.of(new BigDecimal("9.8000")));
    given(
            trackingDifferenceQueryService.findLatestBenchmarkModel(
                TKF100, result.positionReportDate()))
        .willReturn(
            Optional.of(
                new BenchmarkModelTrackingDifference(
                    new BigDecimal("0.000500"), new BigDecimal("0.001"))));

    navNotifier.notify(result);

    verify(notificationService).sendMessage(contains("*NAV/Unit: 9.8440 (+0.45%)*"), eq(SAVINGS));
    verify(notificationService)
        .sendMessage(contains("Tracking Difference (BENCHMARK_MODEL): +0.05% ✅"), eq(SAVINGS));
  }

  @Test
  void notify_stillSendsBaseMessageWhenEnrichmentLookupFails() {
    var result = aNavCalculationResult();
    given(fundNavQueryService.findNavPerUnit(any(), any()))
        .willThrow(new RuntimeException("nav_report read failed"));

    navNotifier.notify(result);

    verify(notificationService).sendMessage(contains("*NAV/Unit: 9.8440*"), eq(SAVINGS));
  }

  private NavCalculationResult aNavCalculationResult() {
    return NavCalculationResult.builder()
        .fund(TKF100)
        .calculationDate(LocalDate.of(2026, 2, 18))
        .securitiesValue(new BigDecimal("6388454.59"))
        .cashPosition(new BigDecimal("504842.47"))
        .receivables(new BigDecimal("1234.56"))
        .pendingSubscriptions(new BigDecimal("25678.90"))
        .pendingRedemptions(new BigDecimal("3456.78"))
        .managementFeeAccrual(new BigDecimal("397.26"))
        .depotFeeAccrual(new BigDecimal("6.85"))
        .payables(new BigDecimal("91782.00"))
        .blackrockAdjustment(new BigDecimal("1234.00"))
        .aum(new BigDecimal("6824567.63"))
        .unitsOutstanding(new BigDecimal("693214.12345"))
        .navPerUnit(new BigDecimal("9.8440"))
        .positionReportDate(LocalDate.of(2026, 2, 17))
        .priceDate(LocalDate.of(2026, 2, 17))
        .calculatedAt(Instant.parse("2026-02-18T13:30:00Z"))
        .securitiesDetail(
            List.of(
                new SecurityDetail(
                    "IE00BMDBMY19",
                    "ESGM.XETRA",
                    new BigDecimal("13288.00000"),
                    new BigDecimal("43.380"),
                    new BigDecimal("576433.44"),
                    LocalDate.of(2026, 2, 17)),
                new SecurityDetail(
                    "IE00BJZ2DC62",
                    "XRSM.XETRA",
                    new BigDecimal("21180.00000"),
                    new BigDecimal("49.550"),
                    new BigDecimal("1049469.00"),
                    LocalDate.of(2026, 2, 12))))
        .build();
  }
}
