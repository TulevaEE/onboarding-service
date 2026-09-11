package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Severity.ERROR;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueQueries;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.savings.RedemptionAlertThresholds;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedemptionAlertJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  // 2025-01-15 = Wednesday; 16:05 Tallinn = 14:05 UTC (EET = UTC+2 in winter)
  private static final String WED_1605_UTC = "2025-01-15T14:05:00Z";
  private static final String SAT_1605_UTC = "2025-01-18T14:05:00Z";

  private static final LocalDate WEDNESDAY = LocalDate.of(2025, 1, 15);

  // Today's 16:00 cutoff in UTC
  private static final Instant WED_CUTOFF = Instant.parse("2025-01-15T14:00:00Z");

  private static final BigDecimal SEEDED_PAYOUT_THRESHOLD = new BigDecimal("40000");
  private static final BigDecimal SEEDED_LIQUIDITY_SHARE_OF_AUM = new BigDecimal("0.01");

  @Mock private RedemptionRequestRepository redemptionRequestRepository;
  @Mock private FundValueQueries fundValueQueries;
  @Mock private OperationsNotificationService notificationService;
  @Mock private PublicHolidays publicHolidays;
  @Mock private RedemptionAlertThresholds redemptionAlertThresholds;

  @Test
  void sendsPayoutWarning_whenTotalExceedsThreshold() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);
    givenPayoutWarningThreshold(SEEDED_PAYOUT_THRESHOLD);
    givenLiquidityWarningShareOfAum(SEEDED_LIQUIDITY_SHARE_OF_AUM);

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("25000.00")),
                requestWithAmount(new BigDecimal("20000.00"))));

    given(fundValueQueries.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(new BigDecimal("50000000.00"))));

    job.checkRedemptionAlerts();

    verify(notificationService).sendMessage(contains("PAYOUT WARNING"), eq(INVESTMENT), eq(ERROR));
    verifyNoMoreInteractions(notificationService);
  }

  @Test
  void sendsLiquidityWarning_whenTotalExceedsOnePercentOfAum() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);
    givenPayoutWarningThreshold(SEEDED_PAYOUT_THRESHOLD);
    givenLiquidityWarningShareOfAum(SEEDED_LIQUIDITY_SHARE_OF_AUM);

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("3000.00")),
                requestWithAmount(new BigDecimal("2500.00"))));

    given(fundValueQueries.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(new BigDecimal("500000.00"))));

    job.checkRedemptionAlerts();

    verify(notificationService)
        .sendMessage(contains("LIQUIDITY WARNING"), eq(INVESTMENT), eq(ERROR));
    verifyNoMoreInteractions(notificationService);
  }

  @Test
  void sendsBothAlerts_whenBothThresholdsExceeded() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);
    givenPayoutWarningThreshold(SEEDED_PAYOUT_THRESHOLD);
    givenLiquidityWarningShareOfAum(SEEDED_LIQUIDITY_SHARE_OF_AUM);

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("25000.00")),
                requestWithAmount(new BigDecimal("20000.00"))));

    given(fundValueQueries.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(new BigDecimal("500000.00"))));

    job.checkRedemptionAlerts();

    verify(notificationService).sendMessage(contains("PAYOUT WARNING"), eq(INVESTMENT), eq(ERROR));
    verify(notificationService)
        .sendMessage(contains("LIQUIDITY WARNING"), eq(INVESTMENT), eq(ERROR));
  }

  @Test
  void silent_whenBelowBothThresholds() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);
    givenPayoutWarningThreshold(SEEDED_PAYOUT_THRESHOLD);
    givenLiquidityWarningShareOfAum(SEEDED_LIQUIDITY_SHARE_OF_AUM);

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("2000.00")),
                requestWithAmount(new BigDecimal("2000.00"))));

    given(fundValueQueries.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(new BigDecimal("500000.00"))));

    job.checkRedemptionAlerts();

    verifyNoInteractions(notificationService);
  }

  @Test
  void silent_whenPayoutExactlyAtThreshold() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);
    givenPayoutWarningThreshold(SEEDED_PAYOUT_THRESHOLD);
    givenLiquidityWarningShareOfAum(SEEDED_LIQUIDITY_SHARE_OF_AUM);

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(List.of(requestWithAmount(new BigDecimal("40000.00"))));

    given(fundValueQueries.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(new BigDecimal("50000000.00"))));

    job.checkRedemptionAlerts();

    verifyNoInteractions(notificationService);
  }

  @Test
  void silent_onNonWorkingDay() {
    var job = jobOn(SAT_1605_UTC);
    given(publicHolidays.isWorkingDay(LocalDate.of(2025, 1, 18))).willReturn(false);

    job.checkRedemptionAlerts();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(redemptionRequestRepository);
    verifyNoInteractions(redemptionAlertThresholds);
  }

  @Test
  void silent_whenNoVerifiedRequests() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(List.of());

    job.checkRedemptionAlerts();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(fundValueQueries);
    verifyNoInteractions(redemptionAlertThresholds);
  }

  @Test
  void skipsLiquidityCheck_whenAumNotAvailable() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);
    givenPayoutWarningThreshold(SEEDED_PAYOUT_THRESHOLD);
    givenLiquidityWarningShareOfAum(SEEDED_LIQUIDITY_SHARE_OF_AUM);

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("25000.00")),
                requestWithAmount(new BigDecimal("20000.00"))));

    given(fundValueQueries.findLastValueForFund(TKF100.getAumKey())).willReturn(Optional.empty());

    job.checkRedemptionAlerts();

    verify(notificationService).sendMessage(contains("PAYOUT WARNING"), eq(INVESTMENT), eq(ERROR));
    verifyNoMoreInteractions(notificationService);
  }

  @Test
  void skipsLiquidityCheck_whenAumIsZero() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);
    givenPayoutWarningThreshold(SEEDED_PAYOUT_THRESHOLD);
    givenLiquidityWarningShareOfAum(SEEDED_LIQUIDITY_SHARE_OF_AUM);

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("25000.00")),
                requestWithAmount(new BigDecimal("20000.00"))));

    given(fundValueQueries.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(BigDecimal.ZERO)));

    job.checkRedemptionAlerts();

    verify(notificationService).sendMessage(contains("PAYOUT WARNING"), eq(INVESTMENT), eq(ERROR));
    verifyNoMoreInteractions(notificationService);
  }

  @Test
  void appliesTheSeededPayoutThreshold() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);
    givenPayoutWarningThreshold(new BigDecimal("20000"));
    givenLiquidityWarningShareOfAum(SEEDED_LIQUIDITY_SHARE_OF_AUM);

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("15000.00")),
                requestWithAmount(new BigDecimal("10000.00"))));

    given(fundValueQueries.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(new BigDecimal("50000000.00"))));

    job.checkRedemptionAlerts();

    verify(notificationService).sendMessage(contains("PAYOUT WARNING"), eq(INVESTMENT), eq(ERROR));
    verifyNoMoreInteractions(notificationService);
  }

  @Test
  void appliesTheSeededLiquidityWarningShareOfAum() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);
    givenPayoutWarningThreshold(SEEDED_PAYOUT_THRESHOLD);
    givenLiquidityWarningShareOfAum(new BigDecimal("0.05"));

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("3000.00")),
                requestWithAmount(new BigDecimal("2500.00"))));

    given(fundValueQueries.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(new BigDecimal("500000.00"))));

    job.checkRedemptionAlerts();

    verifyNoInteractions(notificationService);
  }

  @Test
  void namesTheConfiguredShareOfAumInTheLiquidityAlert() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);
    givenPayoutWarningThreshold(SEEDED_PAYOUT_THRESHOLD);
    givenLiquidityWarningShareOfAum(new BigDecimal("0.02"));

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("6000.00")),
                requestWithAmount(new BigDecimal("5000.00"))));

    given(fundValueQueries.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(new BigDecimal("500000.00"))));

    job.checkRedemptionAlerts();

    String expectedMessage =
        "LIQUIDITY WARNING: TKF100 pending withdrawals totalAmount=11000.00 EUR (2.20% of AUM), requests=2, AUM=500000.00 EUR. Exceeds 2% of AUM.";
    verify(notificationService).sendMessage(expectedMessage, INVESTMENT, ERROR);
    verifyNoMoreInteractions(notificationService);
  }

  @Test
  void skipsPayoutCheck_whenPayoutThresholdNotSeeded() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);
    givenNoPayoutWarningThreshold();
    givenLiquidityWarningShareOfAum(SEEDED_LIQUIDITY_SHARE_OF_AUM);

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("25000.00")),
                requestWithAmount(new BigDecimal("20000.00"))));

    given(fundValueQueries.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(new BigDecimal("50000000.00"))));

    job.checkRedemptionAlerts();

    verifyNoInteractions(notificationService);
  }

  @Test
  void skipsLiquidityCheck_whenLiquidityShareNotSeeded() {
    var job = jobOn(WED_1605_UTC);
    given(publicHolidays.isWorkingDay(WEDNESDAY)).willReturn(true);
    givenPayoutWarningThreshold(SEEDED_PAYOUT_THRESHOLD);
    givenNoLiquidityWarningShareOfAum();

    given(redemptionRequestRepository.findAcceptedBefore(VERIFIED, WED_CUTOFF))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("25000.00")),
                requestWithAmount(new BigDecimal("20000.00"))));

    job.checkRedemptionAlerts();

    verify(notificationService).sendMessage(contains("PAYOUT WARNING"), eq(INVESTMENT), eq(ERROR));
    verifyNoMoreInteractions(notificationService);
    verifyNoInteractions(fundValueQueries);
  }

  private void givenPayoutWarningThreshold(BigDecimal threshold) {
    given(redemptionAlertThresholds.redemptionPayoutWarningThreshold(TKF100, WEDNESDAY))
        .willReturn(Optional.of(threshold));
  }

  private void givenNoPayoutWarningThreshold() {
    given(redemptionAlertThresholds.redemptionPayoutWarningThreshold(TKF100, WEDNESDAY))
        .willReturn(Optional.empty());
  }

  private void givenLiquidityWarningShareOfAum(BigDecimal share) {
    given(redemptionAlertThresholds.redemptionLiquidityWarningShareOfAum(TKF100, WEDNESDAY))
        .willReturn(Optional.of(share));
  }

  private void givenNoLiquidityWarningShareOfAum() {
    given(redemptionAlertThresholds.redemptionLiquidityWarningShareOfAum(TKF100, WEDNESDAY))
        .willReturn(Optional.empty());
  }

  private RedemptionAlertJob jobOn(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), TALLINN);
    return new RedemptionAlertJob(
        clock,
        publicHolidays,
        redemptionRequestRepository,
        fundValueQueries,
        notificationService,
        redemptionAlertThresholds);
  }

  private RedemptionRequest requestWithAmount(BigDecimal amount) {
    return RedemptionRequestFixture.redemptionRequestFixture()
        .requestedAmount(amount)
        .status(VERIFIED)
        .build();
  }

  private FundValue aumValue(BigDecimal value) {
    return new FundValue(TKF100.getAumKey(), LocalDate.of(2025, 1, 14), value, "TULEVA", null);
  }
}
