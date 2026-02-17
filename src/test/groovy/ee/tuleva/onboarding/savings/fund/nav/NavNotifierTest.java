package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.investment.TulevaFund.TKF100;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.SAVINGS;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.investment.calculation.PositionPriceResolver;
import ee.tuleva.onboarding.investment.calculation.ResolvedPrice;
import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavNotifierTest {

  @Mock private OperationsNotificationService notificationService;
  @Mock private NavLedgerRepository navLedgerRepository;
  @Mock private PositionPriceResolver positionPriceResolver;

  @InjectMocks private NavNotifier navNotifier;

  @Test
  void formatMessage_includesAllComponentsAndSecuritiesDetail() {
    var priceDate = LocalDate.of(2026, 2, 17);
    var result = aNavCalculationResult(priceDate);

    when(navLedgerRepository.getSecuritiesUnitBalances())
        .thenReturn(
            Map.of(
                "IE00BMDBMY19", new BigDecimal("13288.00000"),
                "IE00BJZ2DC62", new BigDecimal("21180.00000")));

    when(positionPriceResolver.resolve("IE00BMDBMY19", priceDate))
        .thenReturn(
            Optional.of(ResolvedPrice.builder().usedPrice(new BigDecimal("43.380")).build()));

    when(positionPriceResolver.resolve("IE00BJZ2DC62", priceDate))
        .thenReturn(
            Optional.of(ResolvedPrice.builder().usedPrice(new BigDecimal("49.550")).build()));

    var message = navNotifier.formatMessage(result);

    assertThat(message)
        .contains("TKF100", "2026-02-17")
        .contains("6,388,454.59")
        .contains("504,842.47")
        .contains("1,234.56")
        .contains("25,678.90")
        .contains("3,456.78")
        .contains("397.26")
        .contains("6.85")
        .contains("91,782.00")
        .contains("6,920,210.52")
        .contains("95,642.89")
        .contains("6,824,567.63")
        .contains("693214.12345")
        .contains("9.8448")
        .contains("IE00BMDBMY19", "ESGM.XETRA", "13288", "43.38", "576,433.44")
        .contains("IE00BJZ2DC62", "XRSM.XETRA", "21180", "49.55", "1,049,469.00");
  }

  @Test
  void notify_sendsMessageToSavingsChannel() {
    var result = aNavCalculationResult(LocalDate.of(2026, 2, 17));
    when(navLedgerRepository.getSecuritiesUnitBalances()).thenReturn(Map.of());

    navNotifier.notify(result);

    verify(notificationService).sendMessage(contains("TKF100"), eq(SAVINGS));
  }

  @Test
  void notify_doesNotPropagateExceptions() {
    var result = aNavCalculationResult(LocalDate.of(2026, 2, 17));
    when(navLedgerRepository.getSecuritiesUnitBalances()).thenReturn(Map.of());
    doThrow(new RuntimeException("Slack down")).when(notificationService).sendMessage(any(), any());

    assertThatCode(() -> navNotifier.notify(result)).doesNotThrowAnyException();
  }

  private NavCalculationResult aNavCalculationResult(LocalDate date) {
    return NavCalculationResult.builder()
        .fund(TKF100)
        .calculationDate(date)
        .securitiesValue(new BigDecimal("6388454.59"))
        .cashPosition(new BigDecimal("504842.47"))
        .receivables(new BigDecimal("1234.56"))
        .pendingSubscriptions(new BigDecimal("25678.90"))
        .pendingRedemptions(new BigDecimal("3456.78"))
        .managementFeeAccrual(new BigDecimal("397.26"))
        .depotFeeAccrual(new BigDecimal("6.85"))
        .payables(new BigDecimal("91782.00"))
        .blackrockAdjustment(ZERO)
        .aum(new BigDecimal("6824567.63"))
        .unitsOutstanding(new BigDecimal("693214.12345"))
        .navPerUnit(new BigDecimal("9.8448"))
        .positionReportDate(date)
        .priceDate(date)
        .calculatedAt(Instant.parse("2026-02-17T13:30:00Z"))
        .componentDetails(Map.of())
        .build();
  }
}
