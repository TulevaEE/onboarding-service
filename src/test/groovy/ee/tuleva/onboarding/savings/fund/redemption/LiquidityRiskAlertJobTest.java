package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.WITHDRAWALS;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.persistence.FundValueRepository;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
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
class LiquidityRiskAlertJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  // 2025-01-15 = Wednesday; 16:05 Tallinn = 14:05 UTC (EET = UTC+2 in winter)
  private static final String WED_1605_UTC = "2025-01-15T14:05:00Z";
  private static final String SAT_1605_UTC = "2025-01-18T14:05:00Z";

  @Mock private RedemptionRequestRepository redemptionRequestRepository;
  @Mock private FundValueRepository fundValueRepository;
  @Mock private OperationsNotificationService notificationService;
  @Mock private PublicHolidays publicHolidays;

  @Test
  void sendsAlert_whenWithdrawalsExceedOnePercentOfAum() {
    var job = jobOn(WED_1605_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);

    Instant cutoff = Instant.parse("2025-01-15T14:00:00Z");
    given(redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("3000.00")),
                requestWithAmount(new BigDecimal("2500.00"))));

    given(fundValueRepository.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(new BigDecimal("500000.00"))));

    job.checkLiquidityRisk();

    verify(notificationService).sendMessage(contains("5500.00"), eq(WITHDRAWALS));
  }

  @Test
  void silent_whenWithdrawalsBelowOnePercentOfAum() {
    var job = jobOn(WED_1605_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);

    Instant cutoff = Instant.parse("2025-01-15T14:00:00Z");
    given(redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("2000.00")),
                requestWithAmount(new BigDecimal("2000.00"))));

    given(fundValueRepository.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(new BigDecimal("500000.00"))));

    job.checkLiquidityRisk();

    verifyNoInteractions(notificationService);
  }

  @Test
  void silent_whenWithdrawalsExactlyOnePercentOfAum() {
    var job = jobOn(WED_1605_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);

    Instant cutoff = Instant.parse("2025-01-15T14:00:00Z");
    given(redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff))
        .willReturn(List.of(requestWithAmount(new BigDecimal("5000.00"))));

    given(fundValueRepository.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.of(aumValue(new BigDecimal("500000.00"))));

    job.checkLiquidityRisk();

    verifyNoInteractions(notificationService);
  }

  @Test
  void silent_onNonWorkingDay() {
    var job = jobOn(SAT_1605_UTC);
    LocalDate today = LocalDate.of(2025, 1, 18);
    given(publicHolidays.isWorkingDay(today)).willReturn(false);

    job.checkLiquidityRisk();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(redemptionRequestRepository);
  }

  @Test
  void silent_whenNoVerifiedRequests() {
    var job = jobOn(WED_1605_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);

    Instant cutoff = Instant.parse("2025-01-15T14:00:00Z");
    given(redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff))
        .willReturn(List.of());

    job.checkLiquidityRisk();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(fundValueRepository);
  }

  @Test
  void silent_whenAumNotAvailable() {
    var job = jobOn(WED_1605_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);

    Instant cutoff = Instant.parse("2025-01-15T14:00:00Z");
    given(redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff))
        .willReturn(List.of(requestWithAmount(new BigDecimal("5000.00"))));

    given(fundValueRepository.findLastValueForFund(TKF100.getAumKey()))
        .willReturn(Optional.empty());

    job.checkLiquidityRisk();

    verifyNoInteractions(notificationService);
  }

  private LiquidityRiskAlertJob jobOn(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), TALLINN);
    return new LiquidityRiskAlertJob(
        clock,
        publicHolidays,
        redemptionRequestRepository,
        fundValueRepository,
        notificationService);
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
