package ee.tuleva.onboarding.investment.check.health;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckSeverity.*;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.COMPLETENESS;
import static ee.tuleva.onboarding.investment.check.health.HealthCheckType.ISIN_MATCH;
import static ee.tuleva.onboarding.investment.report.ReportProvider.SEB;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealthCheckNotifierTest {

  private static final LocalDate DATE = LocalDate.of(2026, 4, 15);

  @Mock OperationsNotificationService notificationService;
  @Mock HealthCheckEventRepository eventRepository;
  @InjectMocks HealthCheckNotifier notifier;

  @Test
  void silentWhenNoIssuesAndNoPriorState() {
    var result = new HealthCheckResult(TUK75, DATE, List.of());

    var notified = notifier.notify(SEB, DATE, List.of(result));

    assertThat(notified).isFalse();
    verifyNoInteractions(notificationService);
  }

  @Test
  void sendsImportBlockedOnFirstFail() {
    var finding = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "TUK75: unknown ISIN");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));

    var notified = notifier.notify(SEB, DATE, List.of(result));

    assertThat(notified).isTrue();
    verify(notificationService).sendMessage(contains("IMPORT BLOCKED"), eq(INVESTMENT));
    verify(notificationService)
        .sendMessage(contains("source files need to be fixed"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("[FAIL]"), eq(INVESTMENT));
  }

  @Test
  void sendsWarningWhenNoFails() {
    var finding = new HealthCheckFinding(TUK75, COMPLETENESS, WARNING, "TUK75: no CASH");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));

    var notified = notifier.notify(SEB, DATE, List.of(result));

    assertThat(notified).isTrue();
    verify(notificationService).sendMessage(contains("Import warning"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("[WARNING]"), eq(INVESTMENT));
  }

  @Test
  void includesProviderAndDateInMessage() {
    var finding = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "test");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));

    notifier.notify(SEB, DATE, List.of(result));

    verify(notificationService).sendMessage(contains("SEB"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("2026-04-15"), eq(INVESTMENT));
  }

  @Test
  void silentWhenSeverityUnchangedFromPriorRun() {
    var finding = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "TUK75: unknown ISIN");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));
    givenPreviousSeverity(ISIN_MATCH, FAIL);

    var notified = notifier.notify(SEB, DATE, List.of(result));

    assertThat(notified).isFalse();
    verifyNoInteractions(notificationService);
  }

  @Test
  void sendsWarningWhenSeverityDropsFromFailToWarning() {
    var finding = new HealthCheckFinding(TUK75, ISIN_MATCH, WARNING, "TUK75: soft issue");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));
    givenPreviousSeverity(ISIN_MATCH, FAIL);

    var notified = notifier.notify(SEB, DATE, List.of(result));

    assertThat(notified).isTrue();
    verify(notificationService).sendMessage(contains("Import warning"), eq(INVESTMENT));
  }

  @Test
  void sendsClearedWhenResolvedAfterPriorFail() {
    var result = new HealthCheckResult(TUK75, DATE, List.of());
    givenPreviousSeverity(ISIN_MATCH, FAIL);

    var notified = notifier.notify(SEB, DATE, List.of(result));

    assertThat(notified).isTrue();
    verify(notificationService).sendMessage(contains("Import cleared"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("[CLEARED]"), eq(INVESTMENT));
  }

  @Test
  void swallowsExceptions() {
    doThrow(new RuntimeException("Slack down")).when(notificationService).sendMessage(any(), any());
    var finding = new HealthCheckFinding(TUK75, ISIN_MATCH, FAIL, "test");
    var result = new HealthCheckResult(TUK75, DATE, List.of(finding));

    var notified = notifier.notify(SEB, DATE, List.of(result));

    assertThat(notified).isFalse();
  }

  private void givenPreviousSeverity(HealthCheckType checkType, HealthCheckSeverity severity) {
    var current = HealthCheckEvent.builder().severity(severity).build();
    var previous = HealthCheckEvent.builder().severity(severity).build();
    lenient()
        .doReturn(List.of(current, previous))
        .when(eventRepository)
        .findTop2ByFundAndCheckDateAndCheckTypeOrderByCreatedAtDesc(TUK75, DATE, checkType);
  }
}
