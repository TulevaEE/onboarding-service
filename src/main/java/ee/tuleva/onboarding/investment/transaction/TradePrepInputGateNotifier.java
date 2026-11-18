package ee.tuleva.onboarding.investment.transaction;

import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static java.util.stream.Collectors.joining;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.tulevafund.TulevaFund;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class TradePrepInputGateNotifier {

  private final OperationsNotificationService notificationService;

  void notifyStopped(TulevaFund fund, LocalDate asOfDate, List<InputGateFailure> failures) {
    try {
      notificationService.sendMessage(message(fund, asOfDate, failures), INVESTMENT);
    } catch (RuntimeException e) {
      log.error(
          "Trade prep input gate notification failed: fund={}, asOfDate={}", fund, asOfDate, e);
    }
  }

  private static String message(
      TulevaFund fund, LocalDate asOfDate, List<InputGateFailure> failures) {
    String header =
        "🔴 %s tehingute mustandit ei tehtud (%s). Käivita ettevalmistus uuesti, kui sisend on korras:"
            .formatted(fund.getCode(), asOfDate);
    return Stream.concat(Stream.of(header), failures.stream().map(InputGateFailure::slackLine))
        .collect(joining("\n"));
  }
}
