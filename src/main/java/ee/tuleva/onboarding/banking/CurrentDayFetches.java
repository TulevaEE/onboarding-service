package ee.tuleva.onboarding.banking;

import ee.tuleva.onboarding.banking.event.BankMessageEvents.FetchSebCurrentDayTransactionsRequested;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentDayFetches {

  private final ApplicationEventPublisher eventPublisher;

  public void requestFor(List<BankAccount> accounts) {
    if (accounts.stream().map(this::request).anyMatch(Outcome.GATEWAY_UNAVAILABLE::equals)) {
      log.warn("SEB gateway unavailable, the remaining current-day fetches were not requested");
    }
  }

  private Outcome request(BankAccount account) {
    try {
      eventPublisher.publishEvent(new FetchSebCurrentDayTransactionsRequested(account));
      return Outcome.REQUESTED;
    } catch (RuntimeException exception) {
      log.error("SEB current day transactions fetch failed: account={}", account, exception);
      return isGatewayUnavailable(exception) ? Outcome.GATEWAY_UNAVAILABLE : Outcome.FAILED;
    }
  }

  private static boolean isGatewayUnavailable(RuntimeException exception) {
    return exception instanceof HttpServerErrorException
        || exception instanceof ResourceAccessException;
  }

  private enum Outcome {
    REQUESTED,
    FAILED,
    GATEWAY_UNAVAILABLE
  }
}
