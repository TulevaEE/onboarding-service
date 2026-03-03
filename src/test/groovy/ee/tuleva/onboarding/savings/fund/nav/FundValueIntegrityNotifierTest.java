package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.validation.FundValueIntegrityChecker;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundValueIntegrityNotifierTest {

  @Mock private FundValueIntegrityChecker integrityChecker;
  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private FundValueIntegrityNotifier notifier;

  @Test
  void notifyIntegrityCheck_sendsCheckerSummaryToSavingsChannel() {
    LocalDate endDate = LocalDate.of(2026, 2, 11);
    when(integrityChecker.runIntegrityCheck(endDate))
        .thenReturn("Fund Value Integrity Check Summary");

    notifier.notifyIntegrityCheck(endDate);

    verify(notificationService)
        .sendMessage(contains("Fund Value Integrity Check Summary"), eq(SAVINGS));
  }

  @Test
  void notifyIntegrityCheck_doesNotPropagateExceptions() {
    LocalDate endDate = LocalDate.of(2026, 2, 11);
    when(integrityChecker.runIntegrityCheck(endDate)).thenThrow(new RuntimeException("boom"));

    assertThatCode(() -> notifier.notifyIntegrityCheck(endDate)).doesNotThrowAnyException();
  }
}
