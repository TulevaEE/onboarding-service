package ee.tuleva.onboarding.savings.fund.redemption;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.WITHDRAWALS;
import static ee.tuleva.onboarding.savings.fund.redemption.RedemptionRequest.Status.VERIFIED;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.party.PartyId;
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
class RedemptionPayoutWarningJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  // 2025-01-15 = Wednesday; 13:00 Tallinn = 11:00 UTC (EET = UTC+2 in winter)
  private static final String WED_1300_UTC = "2025-01-15T11:00:00Z";
  private static final String SAT_1300_UTC = "2025-01-18T11:00:00Z";

  @Mock private RedemptionRequestRepository redemptionRequestRepository;
  @Mock private OperationsNotificationService notificationService;
  @Mock private PublicHolidays publicHolidays;

  @Test
  void sendsWarning_whenTotalExceedsThreshold() {
    var job = jobOn(WED_1300_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);
    given(publicHolidays.previousWorkingDay(today)).willReturn(previousWorkingDay);

    // Previous working day's 16:00 Tallinn = 14:00 UTC
    Instant cutoff = Instant.parse("2025-01-14T14:00:00Z");
    given(redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("25000.00")),
                requestWithAmount(new BigDecimal("20000.00"))));

    job.checkPayoutThreshold();

    verify(notificationService).sendMessage(contains("45000.00"), eq(WITHDRAWALS));
  }

  @Test
  void silent_whenTotalBelowThreshold() {
    var job = jobOn(WED_1300_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);
    given(publicHolidays.previousWorkingDay(today)).willReturn(previousWorkingDay);

    Instant cutoff = Instant.parse("2025-01-14T14:00:00Z");
    given(redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff))
        .willReturn(
            List.of(
                requestWithAmount(new BigDecimal("15000.00")),
                requestWithAmount(new BigDecimal("15000.00"))));

    job.checkPayoutThreshold();

    verifyNoInteractions(notificationService);
  }

  @Test
  void silent_onNonWorkingDay() {
    var job = jobOn(SAT_1300_UTC);
    LocalDate today = LocalDate.of(2025, 1, 18);
    given(publicHolidays.isWorkingDay(today)).willReturn(false);

    job.checkPayoutThreshold();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(redemptionRequestRepository);
  }

  @Test
  void silent_whenNoVerifiedRequests() {
    var job = jobOn(WED_1300_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);
    given(publicHolidays.previousWorkingDay(today)).willReturn(previousWorkingDay);

    Instant cutoff = Instant.parse("2025-01-14T14:00:00Z");
    given(redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff))
        .willReturn(List.of());

    job.checkPayoutThreshold();

    verifyNoInteractions(notificationService);
  }

  @Test
  void silent_whenExactlyAtThreshold() {
    var job = jobOn(WED_1300_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    LocalDate previousWorkingDay = LocalDate.of(2025, 1, 14);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);
    given(publicHolidays.previousWorkingDay(today)).willReturn(previousWorkingDay);

    Instant cutoff = Instant.parse("2025-01-14T14:00:00Z");
    given(redemptionRequestRepository.findByStatusAndRequestedAtBefore(VERIFIED, cutoff))
        .willReturn(List.of(requestWithAmount(new BigDecimal("40000.00"))));

    job.checkPayoutThreshold();

    verifyNoInteractions(notificationService);
  }

  private RedemptionPayoutWarningJob jobOn(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), TALLINN);
    return new RedemptionPayoutWarningJob(
        clock, publicHolidays, redemptionRequestRepository, notificationService);
  }

  private RedemptionRequest requestWithAmount(BigDecimal amount) {
    return RedemptionRequest.builder()
        .partyId(new PartyId(PartyId.Type.PERSON, "38501010000"))
        .fundUnits(new BigDecimal("10.00000"))
        .requestedAmount(amount)
        .customerIban("EE123456789012345678")
        .status(VERIFIED)
        .build();
  }
}
