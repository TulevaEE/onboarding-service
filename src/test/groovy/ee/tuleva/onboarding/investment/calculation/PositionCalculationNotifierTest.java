package ee.tuleva.onboarding.investment.calculation;

import static ee.tuleva.onboarding.investment.calculation.TulevaFund.TUK75;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PositionCalculationNotifierTest {

  private static final TulevaFund FUND = TUK75;
  private static final String ISIN = "IE00BFNM3G45";
  private static final LocalDate DATE = LocalDate.of(2025, 1, 10);

  @Mock private PositionCalculationNotificationSender notificationSender;

  @Captor private ArgumentCaptor<String> messageCaptor;

  @InjectMocks private PositionCalculationNotifier notifier;

  @Test
  void notifyPriceDiscrepancy_sendsNotificationWithAllDetails() {
    BigDecimal eodhdPrice = new BigDecimal("100.00");
    BigDecimal yahooPrice = new BigDecimal("101.00");
    BigDecimal discrepancyPercent = new BigDecimal("1.0");

    notifier.notifyPriceDiscrepancy(FUND, ISIN, DATE, eodhdPrice, yahooPrice, discrepancyPercent);

    verify(notificationSender).send(messageCaptor.capture());
    String message = messageCaptor.getValue();
    assertThat(message)
        .contains("Price discrepancy")
        .contains(FUND.name())
        .contains(ISIN)
        .contains("100.00")
        .contains("101.00")
        .contains("1.0");
  }

  @Test
  void notifyYahooMissing_sendsNotificationWithDetails() {
    BigDecimal eodhdPrice = new BigDecimal("100.00");

    notifier.notifyYahooMissing(FUND, ISIN, DATE, eodhdPrice);

    verify(notificationSender).send(messageCaptor.capture());
    String message = messageCaptor.getValue();
    assertThat(message)
        .contains("Yahoo price missing")
        .contains(FUND.name())
        .contains(ISIN)
        .contains("100.00");
  }

  @Test
  void notifyNoPriceData_sendsBlockedNotification() {
    notifier.notifyNoPriceData(FUND, ISIN, DATE);

    verify(notificationSender).send(messageCaptor.capture());
    String message = messageCaptor.getValue();
    assertThat(message)
        .contains("BLOCKED")
        .contains("no price data")
        .contains(FUND.name())
        .contains(ISIN);
  }

  @Test
  void notifyStalePrice_sendsNotificationWithDaysOld() {
    LocalDate priceDate = LocalDate.of(2025, 1, 7);

    notifier.notifyStalePrice(FUND, ISIN, DATE, priceDate);

    verify(notificationSender).send(messageCaptor.capture());
    String message = messageCaptor.getValue();
    assertThat(message)
        .contains("Stale price")
        .contains(FUND.name())
        .contains(ISIN)
        .contains("2025-01-10")
        .contains("2025-01-07")
        .contains("3 days old");
  }

  @Test
  void notifyEodhdMissing_sendsNotificationWithDetails() {
    BigDecimal yahooPrice = new BigDecimal("100.00");

    notifier.notifyEodhdMissing(FUND, ISIN, DATE, yahooPrice);

    verify(notificationSender).send(messageCaptor.capture());
    String message = messageCaptor.getValue();
    assertThat(message)
        .contains("EODHD price missing")
        .contains(FUND.name())
        .contains(ISIN)
        .contains("100.00");
  }
}
