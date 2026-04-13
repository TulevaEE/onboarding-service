package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK00;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.fund.TulevaFund.TUV100;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
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
class NavAlertJobTest {

  private static final ZoneId TALLINN = ZoneId.of("Europe/Tallinn");

  // 2025-01-15 = Wednesday; all times below are UTC+2 → EET (winter)
  private static final String WED_0906_UTC = "2025-01-15T09:06:00Z"; // 11:06 Tallinn
  private static final String WED_1331_UTC = "2025-01-15T13:31:00Z"; // 15:31 Tallinn
  private static final String SAT_1331_UTC = "2025-01-18T13:31:00Z"; // Saturday 15:31 Tallinn
  private static final String SAT_0906_UTC = "2025-01-18T09:06:00Z"; // Saturday 11:06 Tallinn

  @Mock private NavReportRepository navReportRepository;
  @Mock private OperationsNotificationService notificationService;
  @Mock private PublicHolidays publicHolidays;

  @Test
  void pillar2Alert_fires_whenOnlyTuk75Missing() {
    var job = jobOn(WED_0906_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);
    stubPublished(today, TUK00);
    stubMissing(today, TUK75);

    job.alertPillar2IfMissing();

    verify(notificationService).sendMessage(contains("TUK75"), eq(INVESTMENT));
  }

  @Test
  void pillar2Alert_fires_whenBothPillar2Missing() {
    var job = jobOn(WED_0906_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);
    stubMissing(today, TUK75);
    stubMissing(today, TUK00);

    job.alertPillar2IfMissing();

    verify(notificationService)
        .sendMessage(
            org.mockito.ArgumentMatchers.argThat(
                (String msg) -> msg.contains("TUK75") && msg.contains("TUK00")),
            eq(INVESTMENT));
  }

  @Test
  void pillar2Alert_silent_whenBothPillar2Present() {
    var job = jobOn(WED_0906_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);
    stubPublished(today, TUK75);
    stubPublished(today, TUK00);

    job.alertPillar2IfMissing();

    verifyNoInteractions(notificationService);
  }

  @Test
  void pillar2Alert_silent_onNonWorkingDay() {
    var job = jobOn(SAT_0906_UTC);
    LocalDate today = LocalDate.of(2025, 1, 18);
    given(publicHolidays.isWorkingDay(today)).willReturn(false);

    job.alertPillar2IfMissing();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(navReportRepository);
  }

  @Test
  void savingsPillar3Alert_fires_whenOnlyTkf100Missing() {
    var job = jobOn(WED_1331_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);
    stubPublished(today, TUV100);
    stubMissing(today, TKF100);

    job.alertSavingsPillar3IfMissing();

    verify(notificationService).sendMessage(contains("TKF100"), eq(INVESTMENT));
    verify(notificationService, never()).sendMessage(contains("TUV100"), eq(INVESTMENT));
  }

  @Test
  void savingsPillar3Alert_fires_whenBothMissing() {
    var job = jobOn(WED_1331_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);
    stubMissing(today, TKF100);
    stubMissing(today, TUV100);

    job.alertSavingsPillar3IfMissing();

    verify(notificationService)
        .sendMessage(
            org.mockito.ArgumentMatchers.argThat(
                (String msg) -> msg.contains("TKF100") && msg.contains("TUV100")),
            eq(INVESTMENT));
  }

  @Test
  void savingsPillar3Alert_silent_whenBothPresent() {
    var job = jobOn(WED_1331_UTC);
    LocalDate today = LocalDate.of(2025, 1, 15);
    given(publicHolidays.isWorkingDay(today)).willReturn(true);
    stubPublished(today, TKF100);
    stubPublished(today, TUV100);

    job.alertSavingsPillar3IfMissing();

    verifyNoInteractions(notificationService);
  }

  @Test
  void savingsPillar3Alert_silent_onNonWorkingDay() {
    var job = jobOn(SAT_1331_UTC);
    LocalDate today = LocalDate.of(2025, 1, 18);
    given(publicHolidays.isWorkingDay(today)).willReturn(false);

    job.alertSavingsPillar3IfMissing();

    verifyNoInteractions(notificationService);
    verifyNoInteractions(navReportRepository);
  }

  private void stubMissing(LocalDate today, ee.tuleva.onboarding.fund.TulevaFund fund) {
    lenient()
        .when(navReportRepository.findByNavDateAndFundCodeOrderById(today, fund.getCode()))
        .thenReturn(List.of());
  }

  private void stubPublished(LocalDate today, ee.tuleva.onboarding.fund.TulevaFund fund) {
    lenient()
        .when(navReportRepository.findByNavDateAndFundCodeOrderById(today, fund.getCode()))
        .thenReturn(List.of(new NavReportRow()));
  }

  private NavAlertJob jobOn(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), TALLINN);
    return new NavAlertJob(navReportRepository, notificationService, publicHolidays, clock);
  }
}
