package ee.tuleva.onboarding.investment.check.tracking;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.BENCHMARK_MODEL;
import static ee.tuleva.onboarding.investment.check.tracking.TrackingCheckType.MODEL_PORTFOLIO;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackingDifferenceNotifierTest {

  @Mock OperationsNotificationService notificationService;
  @Mock TrackingDifferenceCalculator calculator;

  @InjectMocks TrackingDifferenceNotifier notifier;

  @BeforeEach
  void setUp() {
    given(calculator.breachThreshold(any(LocalDate.class))).willReturn(new BigDecimal("0.002"));
    given(calculator.escalationThresholdDays(any(LocalDate.class))).willReturn(3);
    given(calculator.escalationNetTdThreshold(any(LocalDate.class)))
        .willReturn(new BigDecimal("0.005"));
  }

  @Test
  void sendsAllClearWhenNoBreaches() {
    var result = result(false, 0, BigDecimal.ZERO);

    notifier.notify(List.of(result));

    then(notificationService).should().sendMessage(contains("within limits"), eq(INVESTMENT));
  }

  @Test
  void sendsBreachNotification() {
    var result = result(true, 1, new BigDecimal("0.0015"));

    notifier.notify(List.of(result));

    then(notificationService).should().sendMessage(contains("TD BREACH DETECTED"), eq(INVESTMENT));
  }

  @Test
  void includesFullAttributionDetail() {
    var attributions =
        List.of(
            new SecurityAttribution(
                "IE00A",
                new BigDecimal("0.60"),
                new BigDecimal("0.55"),
                new BigDecimal("-0.05"),
                new BigDecimal("0.02"),
                null,
                new BigDecimal("-0.001")),
            new SecurityAttribution(
                "IE00B",
                new BigDecimal("0.20"),
                new BigDecimal("0.25"),
                new BigDecimal("0.05"),
                new BigDecimal("-0.01"),
                null,
                new BigDecimal("-0.0005")),
            new SecurityAttribution(
                "IE00C",
                new BigDecimal("0.10"),
                new BigDecimal("0.10"),
                new BigDecimal("0.00"),
                new BigDecimal("0.005"),
                null,
                new BigDecimal("0.0000")),
            new SecurityAttribution(
                "IE00D",
                new BigDecimal("0.10"),
                new BigDecimal("0.10"),
                new BigDecimal("0.00"),
                new BigDecimal("-0.003"),
                null,
                new BigDecimal("0.0000")));

    var result =
        TrackingDifferenceResult.builder()
            .fund(TUK75)
            .checkDate(LocalDate.of(2026, 4, 3))
            .checkType(MODEL_PORTFOLIO)
            .trackingDifference(new BigDecimal("0.0015"))
            .fundReturn(new BigDecimal("0.0100"))
            .benchmarkReturn(new BigDecimal("0.0085"))
            .breach(true)
            .consecutiveBreachDays(1)
            .consecutiveNetTd(new BigDecimal("0.0015"))
            .securityAttributions(attributions)
            .cashDrag(new BigDecimal("-0.001"))
            .feeDrag(new BigDecimal("-0.00001"))
            .residual(new BigDecimal("0.00051"))
            .build();

    notifier.notify(List.of(result));

    var captor = org.mockito.ArgumentCaptor.forClass(String.class);
    then(notificationService).should().sendMessage(captor.capture(), eq(INVESTMENT));
    var message = captor.getValue();

    assertThat(message).contains("IE00A").contains("IE00B").contains("IE00C").contains("IE00D");
    assertThat(message).contains("weight");
    assertThat(message).contains("Fee drag");
    assertThat(message).contains("Residual");
  }

  @Test
  void sendsEscalationWhenThreeConsecutiveDaysAndNetSumExceedsThreshold() {
    var result = result(true, 3, new BigDecimal("0.005"));

    notifier.notify(List.of(result));

    then(notificationService).should().sendMessage(contains("TD ESCALATION"), eq(INVESTMENT));
  }

  @Test
  void escalationShowsCompoundedReturnsAndMultiDayAttribution() {
    var attrs =
        java.util.Map.of(
            "IE00BFG1TM61", new BigDecimal("0.0020"),
            "IE0009FT4LX4", new BigDecimal("-0.0008"));
    var result =
        TrackingDifferenceResult.builder()
            .fund(TUK75)
            .checkDate(LocalDate.of(2026, 4, 5))
            .checkType(MODEL_PORTFOLIO)
            .trackingDifference(new BigDecimal("0.0015"))
            .fundReturn(new BigDecimal("0.0320"))
            .benchmarkReturn(new BigDecimal("0.0275"))
            .breach(true)
            .consecutiveBreachDays(4)
            .consecutiveNetTd(new BigDecimal("0.0060"))
            .compoundedFundReturn(new BigDecimal("0.0320"))
            .compoundedBenchmarkReturn(new BigDecimal("0.0275"))
            .escalationAttributions(attrs)
            .escalationCashDrag(new BigDecimal("-0.0012"))
            .escalationFeeDrag(new BigDecimal("-0.0005"))
            .escalationResidual(new BigDecimal("0.0002"))
            .securityAttributions(List.of())
            .cashDrag(BigDecimal.ZERO)
            .feeDrag(BigDecimal.ZERO)
            .residual(BigDecimal.ZERO)
            .build();

    notifier.notify(List.of(result));

    var captor = org.mockito.ArgumentCaptor.forClass(String.class);
    then(notificationService).should().sendMessage(captor.capture(), eq(INVESTMENT));
    var message = captor.getValue();
    assertThat(message).contains("TD ESCALATION");
    assertThat(message).contains("4 consecutive days");
    assertThat(message).contains("Compounded: fund=");
    assertThat(message).contains("Multi-day attribution:");
    assertThat(message).contains("IE00BFG1TM61");
    assertThat(message).contains("Cash drag:");
    assertThat(message).contains("Fee drag:");
    assertThat(message).contains("Residual:");
  }

  @Test
  void includesActionHintForModelPortfolio() {
    var result = result(true, 1, new BigDecimal("0.0015"));

    notifier.notify(List.of(result));

    var captor = org.mockito.ArgumentCaptor.forClass(String.class);
    then(notificationService).should().sendMessage(captor.capture(), eq(INVESTMENT));
    assertThat(captor.getValue()).contains("check NAV calculation");
  }

  @Test
  void includesActionHintAndAttributionForBenchmarkModel() {
    var attributions =
        List.of(
            new SecurityAttribution(
                "IE00BFG1TM61",
                null,
                new BigDecimal("0.40"),
                null,
                new BigDecimal("0.015"),
                new BigDecimal("0.010"),
                new BigDecimal("0.002")),
            new SecurityAttribution(
                "IE00BKPTWY98",
                null,
                new BigDecimal("0.20"),
                null,
                new BigDecimal("0.008"),
                new BigDecimal("0.012"),
                new BigDecimal("-0.0008")));

    var result =
        TrackingDifferenceResult.builder()
            .fund(TUK75)
            .checkDate(LocalDate.of(2026, 4, 3))
            .checkType(BENCHMARK_MODEL)
            .trackingDifference(new BigDecimal("0.005"))
            .fundReturn(new BigDecimal("0.0100"))
            .benchmarkReturn(new BigDecimal("0.0050"))
            .breach(true)
            .consecutiveBreachDays(1)
            .consecutiveNetTd(new BigDecimal("0.005"))
            .securityAttributions(attributions)
            .cashDrag(BigDecimal.ZERO)
            .feeDrag(BigDecimal.ZERO)
            .residual(BigDecimal.ZERO)
            .build();

    notifier.notify(List.of(result));

    var captor = org.mockito.ArgumentCaptor.forClass(String.class);
    then(notificationService).should().sendMessage(captor.capture(), eq(INVESTMENT));
    var message = captor.getValue();
    assertThat(message).contains("review instrument prices and benchmark data");
    assertThat(message).contains("IE00BFG1TM61").contains("IE00BKPTWY98");
    assertThat(message).contains("instrument").contains("benchmark").contains("diff");
  }

  @Test
  void filtersBenchmarkAcwiFromSlackAlerts() {
    var benchmarkResult =
        TrackingDifferenceResult.builder()
            .fund(TUK75)
            .checkDate(LocalDate.of(2026, 4, 3))
            .checkType(BENCHMARK)
            .trackingDifference(new BigDecimal("0.005"))
            .fundReturn(new BigDecimal("0.0100"))
            .benchmarkReturn(new BigDecimal("0.0050"))
            .breach(true)
            .consecutiveBreachDays(1)
            .consecutiveNetTd(new BigDecimal("0.005"))
            .securityAttributions(List.of())
            .cashDrag(BigDecimal.ZERO)
            .feeDrag(BigDecimal.ZERO)
            .residual(BigDecimal.ZERO)
            .build();

    notifier.notify(List.of(benchmarkResult));

    then(notificationService).should().sendMessage(contains("within limits"), eq(INVESTMENT));
  }

  @Test
  void noEscalationWhenNetSumBelowThreshold() {
    // 3 consecutive days but net sum below the 0.002 threshold
    var result = result(true, 3, new BigDecimal("0.001"));

    notifier.notify(List.of(result));

    then(notificationService).should().sendMessage(contains("TD BREACH DETECTED"), eq(INVESTMENT));
  }

  private TrackingDifferenceResult result(
      boolean breach, int consecutiveBreachDays, BigDecimal consecutiveNetTd) {
    return TrackingDifferenceResult.builder()
        .fund(TUK75)
        .checkDate(LocalDate.of(2026, 4, 3))
        .checkType(MODEL_PORTFOLIO)
        .trackingDifference(breach ? new BigDecimal("0.0015") : new BigDecimal("0.0005"))
        .fundReturn(new BigDecimal("0.0100"))
        .benchmarkReturn(new BigDecimal("0.0085"))
        .breach(breach)
        .consecutiveBreachDays(consecutiveBreachDays)
        .consecutiveNetTd(consecutiveNetTd)
        .securityAttributions(List.of())
        .cashDrag(BigDecimal.ZERO)
        .feeDrag(BigDecimal.ZERO)
        .residual(BigDecimal.ZERO)
        .build();
  }
}
