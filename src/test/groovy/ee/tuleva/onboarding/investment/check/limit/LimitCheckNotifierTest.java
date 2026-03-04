package ee.tuleva.onboarding.investment.check.limit;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.check.limit.BreachSeverity.*;
import static ee.tuleva.onboarding.investment.portfolio.Provider.ISHARES;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimitCheckNotifierTest {

  @Mock OperationsNotificationService notificationService;
  @InjectMocks LimitCheckNotifier notifier;

  @Test
  void sendsBreachNotificationToInvestmentChannel() {
    var breach =
        new PositionBreach(
            TUK75,
            "IE001",
            "iShares MSCI World",
            new BigDecimal("16.50"),
            new BigDecimal("15"),
            new BigDecimal("20"),
            SOFT);
    var result =
        new LimitCheckResult(
            TUK75, LocalDate.of(2026, 3, 4), List.of(breach), List.of(), null, null);

    notifier.notify(List.of(result));

    verify(notificationService).sendMessage(contains("LIMIT BREACH"), eq(INVESTMENT));
  }

  @Test
  void sendsAllClearWhenNoBreaches() {
    var result =
        new LimitCheckResult(TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(), null, null);

    notifier.notify(List.of(result));

    verify(notificationService).sendMessage(contains("all funds within limits"), eq(INVESTMENT));
  }

  @Test
  void includesProviderBreachDetails() {
    var breach =
        new ProviderBreach(
            TUK75, ISHARES, new BigDecimal("35"), new BigDecimal("30"), new BigDecimal("40"), SOFT);
    var result =
        new LimitCheckResult(
            TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(breach), null, null);

    notifier.notify(List.of(result));

    verify(notificationService).sendMessage(contains("ISHARES"), eq(INVESTMENT));
  }

  @Test
  void includesReserveBreachDetails() {
    var breach =
        new ReserveBreach(
            TUK75, new BigDecimal("40000"), new BigDecimal("50000"), new BigDecimal("30000"), SOFT);
    var result =
        new LimitCheckResult(TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(), breach, null);

    notifier.notify(List.of(result));

    verify(notificationService).sendMessage(contains("RESERVE"), eq(INVESTMENT));
  }

  @Test
  void includesFreeCashBreachDetails() {
    var breach = new FreeCashBreach(TUK75, new BigDecimal("25000"), new BigDecimal("10000"), HARD);
    var result =
        new LimitCheckResult(TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(), null, breach);

    notifier.notify(List.of(result));

    verify(notificationService).sendMessage(contains("FREE_CASH"), eq(INVESTMENT));
  }

  @Test
  void swallowsExceptions() {
    doThrow(new RuntimeException("Slack down")).when(notificationService).sendMessage(any(), any());
    var result =
        new LimitCheckResult(TUK75, LocalDate.of(2026, 3, 4), List.of(), List.of(), null, null);

    notifier.notify(List.of(result));
  }
}
