package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;

import ee.tuleva.onboarding.comparisons.fundvalue.validation.FundValueIntegrityChecker;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class FundValueIntegrityNotifier {

  private final FundValueIntegrityChecker integrityChecker;
  private final OperationsNotificationService notificationService;

  void notifyIntegrityCheck(LocalDate endDate) {
    try {
      String summary = integrityChecker.runIntegrityCheck(endDate);
      notificationService.sendMessage(summary, SAVINGS);
    } catch (Exception e) {
      log.error("Failed to run fund value integrity check: endDate={}", endDate, e);
    }
  }
}
