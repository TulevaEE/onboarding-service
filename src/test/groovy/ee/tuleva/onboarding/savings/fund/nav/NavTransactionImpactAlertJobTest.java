package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.investment.config.InvestmentParameter.NAV_IMPACT_VOLUME_THRESHOLD;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.RESERVED;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static java.math.BigDecimal.ZERO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.investment.config.InvestmentParameterRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.savings.fund.SavingFundPayment;
import ee.tuleva.onboarding.savings.fund.SavingFundPaymentRepository;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest;
import ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequestRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavTransactionImpactAlertJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");
  private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
  private static final BigDecimal NAV = new BigDecimal("1.1234");

  @Mock private SavingFundPaymentRepository savingFundPaymentRepository;
  @Mock private RedemptionRequestRepository redemptionRequestRepository;
  @Mock private FundNavProvider fundNavProvider;
  @Mock private InvestmentParameterRepository investmentParameterRepository;
  @Mock private OperationsNotificationService notificationService;
  private final PublicHolidays publicHolidays = new PublicHolidays();

  // --- Non-working day ---

  @Test
  void silent_onNonWorkingDay() {
    // 2026-01-17 = Saturday
    var job = jobOn("2026-01-17T08:00:00Z");

    job.onFundPositionsImported();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(savingFundPaymentRepository);
  }

  // --- TKF100 volume-based ---

  @Test
  void tkf100_noAlert_whenBelowThreshold() {
    // 2026-03-10 = Tuesday, regular day
    var job = jobOn("2026-03-10T08:00:00Z");
    stubThreshold(ONE_MILLION);
    stubTkf100Subscriptions(new BigDecimal("50000"));
    stubTkf100Redemptions(List.of());

    job.onFundPositionsImported();

    verify(notificationService, never()).sendMessage(contains("TKF100"), eq(SAVINGS));
  }

  @Test
  void tkf100_alert_whenAboveThreshold() {
    // 2026-03-10 = Tuesday, regular day
    var job = jobOn("2026-03-10T08:00:00Z");
    stubThreshold(ONE_MILLION);
    stubTkf100Subscriptions(new BigDecimal("800000"));
    stubTkf100Redemptions(List.of(redemption(new BigDecimal("200000"))));
    stubNav(NAV);

    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("TKF100"), eq(SAVINGS));
  }

  @Test
  void tkf100_alert_showsYellowIcon() {
    // 2026-03-10 = Tuesday, regular day
    var job = jobOn("2026-03-10T08:00:00Z");
    stubThreshold(ONE_MILLION);
    stubTkf100Subscriptions(new BigDecimal("800000"));
    stubTkf100Redemptions(List.of(redemption(new BigDecimal("200000"))));
    stubNav(NAV);

    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("⚠️ NAV IMPACT ALERT — TKF100"), eq(SAVINGS));
  }

  @Test
  void tkf100_redemptionEur_estimatedFromUnitsTimesNav() {
    // 2026-03-10 = Tuesday
    var job = jobOn("2026-03-10T08:00:00Z");
    stubThreshold(new BigDecimal("500"));
    var nav = new BigDecimal("1.50000");
    stubNav(nav);
    stubTkf100Subscriptions(ZERO);
    // 400 units × 1.50 = 600 EUR → above 500 threshold
    stubTkf100Redemptions(List.of(redemption(new BigDecimal("400"))));

    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("TKF100"), eq(SAVINGS));
  }

  // --- PEVA/RAVA execution dates ---

  @Test
  void pevaRava_alert_onJan2_firstBizDayAfterJan1() {
    // 2026-01-02 = Friday (Jan 1 is holiday, Jan 2 is 1st biz day after)
    var job = jobOn("2026-01-02T08:00:00Z");
    stubThresholdLenient(ONE_MILLION);
    stubTkf100SubscriptionsLenient(ZERO);
    stubTkf100RedemptionsLenient(List.of());
    stubNavLenient(NAV);

    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("PEVA/RAVA"), eq(SAVINGS));
  }

  @Test
  void pevaRava_alert_showsYellowIcon() {
    // 2026-01-02 = PEVA/RAVA execution date
    var job = jobOn("2026-01-02T08:00:00Z");
    stubThresholdLenient(ONE_MILLION);
    stubTkf100SubscriptionsLenient(ZERO);
    stubTkf100RedemptionsLenient(List.of());
    stubNavLenient(NAV);

    job.onFundPositionsImported();

    verify(notificationService)
        .sendMessage(contains("⚠️ NAV IMPACT ALERT — PEVA/RAVA"), eq(SAVINGS));
  }

  @Test
  void pevaRava_alert_onMay4_firstBizDayAfterMay1() {
    // 2026-05-01 = Friday (holiday), May 2 = Sat, May 3 = Sun → May 4 = Monday
    var job = jobOn("2026-05-04T08:00:00Z");
    stubThresholdLenient(ONE_MILLION);
    stubTkf100SubscriptionsLenient(ZERO);
    stubTkf100RedemptionsLenient(List.of());
    stubNavLenient(NAV);

    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("PEVA/RAVA"), eq(SAVINGS));
  }

  @Test
  void pevaRava_alert_onSep1_whenBusinessDay() {
    // 2026-09-01 = Tuesday (business day)
    var job = jobOn("2026-09-01T08:00:00Z");
    stubThresholdLenient(ONE_MILLION);
    stubTkf100SubscriptionsLenient(ZERO);
    stubTkf100RedemptionsLenient(List.of());
    stubNavLenient(NAV);

    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("PEVA/RAVA"), eq(SAVINGS));
  }

  @Test
  void pevaRava_noAlert_onRegularDay() {
    // 2026-03-10 = Tuesday, no PEVA/RAVA
    var job = jobOn("2026-03-10T08:00:00Z");
    stubThreshold(ONE_MILLION);
    stubTkf100Subscriptions(ZERO);
    stubTkf100Redemptions(List.of());

    job.onFundPositionsImported();

    verify(notificationService, never()).sendMessage(contains("PEVA/RAVA"), eq(SAVINGS));
  }

  // --- R16 booking day (15th of month) ---

  @Test
  void r16_alert_on15thOfMonth() {
    // 2026-03-16 = Monday (March 15 is Sunday → adjusted to Monday 16th)
    var job = jobOn("2026-03-16T08:00:00Z");
    stubThresholdLenient(ONE_MILLION);
    stubTkf100SubscriptionsLenient(ZERO);
    stubTkf100RedemptionsLenient(List.of());
    stubNavLenient(NAV);

    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("R16"), eq(SAVINGS));
  }

  @Test
  void r16_alert_showsYellowIcon() {
    // 2026-04-15 = Wednesday (business day, 15th is the booking day)
    var job = jobOn("2026-04-15T08:00:00Z");
    stubThresholdLenient(ONE_MILLION);
    stubTkf100SubscriptionsLenient(ZERO);
    stubTkf100RedemptionsLenient(List.of());
    stubNavLenient(NAV);

    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("⚠️ NAV IMPACT ALERT — R16"), eq(SAVINGS));
  }

  @Test
  void r16_alert_on15thWhenBusinessDay() {
    // 2026-04-15 = Wednesday (business day, 15th is directly the booking day)
    var job = jobOn("2026-04-15T08:00:00Z");
    stubThresholdLenient(ONE_MILLION);
    stubTkf100SubscriptionsLenient(ZERO);
    stubTkf100RedemptionsLenient(List.of());
    stubNavLenient(NAV);

    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("R16"), eq(SAVINGS));
  }

  @Test
  void r16_noAlert_on16thWhen15thIsBusinessDay() {
    // 2026-04-16 = Thursday (15th was Wednesday = biz day, so 16th is NOT booking day)
    var job = jobOn("2026-04-16T08:00:00Z");
    stubThreshold(ONE_MILLION);
    stubTkf100Subscriptions(ZERO);
    stubTkf100Redemptions(List.of());

    job.onFundPositionsImported();

    verify(notificationService, never()).sendMessage(contains("R16"), eq(SAVINGS));
  }

  @Test
  void r16_noAlert_onRegularDay() {
    // 2026-03-10 = Tuesday, not near 15th
    var job = jobOn("2026-03-10T08:00:00Z");
    stubThreshold(ONE_MILLION);
    stubTkf100Subscriptions(ZERO);
    stubTkf100Redemptions(List.of());

    job.onFundPositionsImported();

    verify(notificationService, never()).sendMessage(contains("R16"), eq(SAVINGS));
  }

  // --- Exception isolation ---

  @Test
  void exceptionInTkf100_doesNotBlockPensionFundAlerts() {
    // 2026-01-02 = PEVA/RAVA execution date
    var job = jobOn("2026-01-02T08:00:00Z");
    given(investmentParameterRepository.findLatestValue(any(), any(LocalDate.class)))
        .willThrow(new RuntimeException("DB down"));

    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("PEVA/RAVA"), eq(SAVINGS));
  }

  // --- Idempotency ---

  @Test
  void duplicateEvent_sameDay_doesNotSendAlertTwice() {
    // 2026-01-02 = PEVA/RAVA execution date
    var job = jobOn("2026-01-02T08:00:00Z");
    stubThresholdLenient(ONE_MILLION);
    stubTkf100SubscriptionsLenient(ZERO);
    stubTkf100RedemptionsLenient(List.of());
    stubNavLenient(NAV);

    job.onFundPositionsImported();
    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("PEVA/RAVA"), eq(SAVINGS));
  }

  // --- NAV failure fallback ---

  @Test
  void tkf100_fallsBackToRequestedAmount_whenNavLookupFails() {
    // 2026-03-10 = Tuesday
    var job = jobOn("2026-03-10T08:00:00Z");
    stubThreshold(new BigDecimal("500"));
    stubTkf100Subscriptions(ZERO);
    given(fundNavProvider.getDisplayNav(TulevaFund.TKF100))
        .willThrow(new IllegalStateException("Stale NAV"));
    // requestedAmount = 400 * 1.1234 = 449.36 (from redemption helper) → below 500
    stubTkf100Redemptions(List.of(redemption(new BigDecimal("400"))));

    job.onFundPositionsImported();

    verify(notificationService, never()).sendMessage(contains("TKF100"), eq(SAVINGS));
  }

  @Test
  void tkf100_alertsWithFallbackRedemptionEstimate_whenNavLookupFails() {
    // 2026-03-10 = Tuesday
    var job = jobOn("2026-03-10T08:00:00Z");
    stubThreshold(ONE_MILLION);
    given(fundNavProvider.getDisplayNav(TulevaFund.TKF100))
        .willThrow(new IllegalStateException("Stale NAV"));
    stubTkf100Subscriptions(new BigDecimal("900000"));
    // requestedAmount = 100000 * 1.1234 = 112340 (fallback), total > 1M
    stubTkf100Redemptions(List.of(redemption(new BigDecimal("100000"))));

    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("TKF100"), eq(SAVINGS));
  }

  // --- Cutoff boundary ---

  @Test
  void tkf100_excludesPaymentsAfterCutoff() {
    // 2026-03-10 = Tuesday, navDate = 2026-03-09, cutoff = 2026-03-09 16:00 Tallinn = 14:00 UTC
    var job = jobOn("2026-03-10T08:00:00Z");
    stubThreshold(new BigDecimal("100"));
    stubTkf100Redemptions(List.of());

    var beforeCutoff =
        SavingFundPayment.builder()
            .amount(new BigDecimal("200"))
            .receivedBefore(Instant.parse("2026-03-09T12:00:00Z"))
            .status(RESERVED)
            .build();
    var afterCutoff =
        SavingFundPayment.builder()
            .amount(new BigDecimal("900000"))
            .receivedBefore(Instant.parse("2026-03-09T14:00:00Z"))
            .status(RESERVED)
            .build();
    given(savingFundPaymentRepository.findPaymentsWithStatus(RESERVED))
        .willReturn(List.of(beforeCutoff, afterCutoff));

    job.onFundPositionsImported();

    verify(notificationService).sendMessage(contains("200.00"), eq(SAVINGS));
  }

  // --- Helpers ---

  private NavTransactionImpactAlertJob jobOn(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), TALLINN);
    return new NavTransactionImpactAlertJob(
        savingFundPaymentRepository,
        redemptionRequestRepository,
        fundNavProvider,
        investmentParameterRepository,
        notificationService,
        publicHolidays,
        clock);
  }

  private void stubThreshold(BigDecimal value) {
    given(
            investmentParameterRepository.findLatestValue(
                eq(NAV_IMPACT_VOLUME_THRESHOLD), any(LocalDate.class)))
        .willReturn(value);
  }

  private void stubThresholdLenient(BigDecimal value) {
    lenient()
        .when(
            investmentParameterRepository.findLatestValue(
                eq(NAV_IMPACT_VOLUME_THRESHOLD), any(LocalDate.class)))
        .thenReturn(value);
  }

  private void stubTkf100Subscriptions(BigDecimal totalAmount) {
    var payment =
        SavingFundPayment.builder()
            .amount(totalAmount)
            .receivedBefore(Instant.parse("2026-01-01T10:00:00Z"))
            .status(RESERVED)
            .build();
    given(savingFundPaymentRepository.findPaymentsWithStatus(RESERVED))
        .willReturn(totalAmount.signum() > 0 ? List.of(payment) : List.of());
  }

  private void stubTkf100SubscriptionsLenient(BigDecimal totalAmount) {
    var payment =
        SavingFundPayment.builder()
            .amount(totalAmount)
            .receivedBefore(Instant.parse("2026-01-01T10:00:00Z"))
            .status(RESERVED)
            .build();
    lenient()
        .when(savingFundPaymentRepository.findPaymentsWithStatus(RESERVED))
        .thenReturn(totalAmount.signum() > 0 ? List.of(payment) : List.of());
  }

  private void stubTkf100Redemptions(List<RedemptionRequest> redemptions) {
    given(
            redemptionRequestRepository.findByStatusAndRequestedAtBefore(
                eq(VERIFIED), any(Instant.class)))
        .willReturn(redemptions);
  }

  private void stubTkf100RedemptionsLenient(List<RedemptionRequest> redemptions) {
    lenient()
        .when(
            redemptionRequestRepository.findByStatusAndRequestedAtBefore(
                eq(VERIFIED), any(Instant.class)))
        .thenReturn(redemptions);
  }

  private void stubNav(BigDecimal nav) {
    given(fundNavProvider.getDisplayNav(TulevaFund.TKF100)).willReturn(nav);
  }

  private void stubNavLenient(BigDecimal nav) {
    lenient().when(fundNavProvider.getDisplayNav(TulevaFund.TKF100)).thenReturn(nav);
  }

  private RedemptionRequest redemption(BigDecimal fundUnits) {
    return RedemptionRequest.builder()
        .userId(1L)
        .partyId(new PartyId(PartyId.Type.PERSON, "38001010000"))
        .fundUnits(fundUnits)
        .requestedAmount(fundUnits.multiply(NAV))
        .customerIban("EE123456789012345678")
        .status(VERIFIED)
        .build();
  }
}
