package ee.tuleva.onboarding.savings.fund.nav;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.INVESTMENT;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.comparisons.fundvalue.FundValue;
import ee.tuleva.onboarding.comparisons.fundvalue.FundValueProvider;
import ee.tuleva.onboarding.fund.TulevaFund;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NavReconciliationServiceTest {

  @Mock private NavReportRepository navReportRepository;
  @Mock private FundValueProvider fundValueProvider;
  @Mock private OperationsNotificationService notificationService;

  @InjectMocks private NavReconciliationService reconciliationService;

  private static final LocalDate NAV_DATE = LocalDate.of(2026, 5, 6);
  private static final Instant CALC_TIME = Instant.parse("2026-05-07T12:00:00Z");

  @BeforeEach
  void setUp() {
    for (TulevaFund fund : TulevaFund.values()) {
      if (fund.hasNavCalculation()) {
        lenient()
            .when(navReportRepository.findLatestByNavDateAndFundCode(NAV_DATE, fund.getCode()))
            .thenReturn(List.of());
      }
    }
  }

  @Test
  void reconcile_silent_whenNavAndAumMatch() {
    stubNavReport(TKF100, NAV_DATE, "9.6994", "969941.07");
    stubFundValue(TKF100.getIsin(), NAV_DATE, "9.6994");
    stubFundValue(TKF100.getAumKey(), NAV_DATE, "969941.07");

    reconciliationService.reconcile(NAV_DATE);

    verifyNoInteractions(notificationService);
  }

  @Test
  void reconcile_alerts_whenNavMismatch() {
    stubNavReport(TKF100, NAV_DATE, "9.6994", "969941.07");
    stubFundValue(TKF100.getIsin(), NAV_DATE, "9.7000");
    stubFundValue(TKF100.getAumKey(), NAV_DATE, "969941.07");

    reconciliationService.reconcile(NAV_DATE);

    verify(notificationService).sendMessage(contains("TKF100"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("NAV mismatch"), eq(INVESTMENT));
  }

  @Test
  void reconcile_alerts_whenAumMismatch() {
    stubNavReport(TKF100, NAV_DATE, "9.6994", "969941.07");
    stubFundValue(TKF100.getIsin(), NAV_DATE, "9.6994");
    stubFundValue(TKF100.getAumKey(), NAV_DATE, "970000.00");

    reconciliationService.reconcile(NAV_DATE);

    verify(notificationService).sendMessage(contains("TKF100"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("AUM mismatch"), eq(INVESTMENT));
  }

  @Test
  void reconcile_alerts_whenFundValueMissing() {
    stubNavReport(TKF100, NAV_DATE, "9.6994", "969941.07");
    when(fundValueProvider.getValueForDate(TKF100.getIsin(), NAV_DATE))
        .thenReturn(Optional.empty());
    stubFundValue(TKF100.getAumKey(), NAV_DATE, "969941.07");

    reconciliationService.reconcile(NAV_DATE);

    verify(notificationService).sendMessage(contains("TKF100"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("missing"), eq(INVESTMENT));
  }

  @Test
  void reconcile_alerts_whenAumFundValueMissing() {
    stubNavReport(TKF100, NAV_DATE, "9.6994", "969941.07");
    stubFundValue(TKF100.getIsin(), NAV_DATE, "9.6994");
    when(fundValueProvider.getValueForDate(TKF100.getAumKey(), NAV_DATE))
        .thenReturn(Optional.empty());

    reconciliationService.reconcile(NAV_DATE);

    verify(notificationService).sendMessage(contains("TKF100"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("AUM fund_value missing"), eq(INVESTMENT));
  }

  @Test
  void reconcile_silent_whenNoNavReportRows() {
    reconciliationService.reconcile(NAV_DATE);

    verify(notificationService, never()).sendMessage(contains("TKF100"), eq(INVESTMENT));
  }

  @Test
  void reconcile_checksPillar2Funds() {
    stubNavReport(TUK75, NAV_DATE, "1.50000", "15000000.00");
    stubFundValue(TUK75.getIsin(), NAV_DATE, "1.49999");
    stubFundValue(TUK75.getAumKey(), NAV_DATE, "15000000.00");

    reconciliationService.reconcile(NAV_DATE);

    verify(notificationService).sendMessage(contains("TUK75"), eq(INVESTMENT));
    verify(notificationService).sendMessage(contains("NAV mismatch"), eq(INVESTMENT));
  }

  private void stubNavReport(TulevaFund fund, LocalDate navDate, String navPerUnit, String aum) {
    var navRow =
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode(fund.getCode())
            .accountType("NAV")
            .accountName("Net Asset Value")
            .quantity(new BigDecimal("1.00"))
            .marketPrice(new BigDecimal(navPerUnit))
            .marketValue(new BigDecimal(navPerUnit))
            .build();

    var unitsRow =
        NavReportRow.builder()
            .navDate(navDate)
            .fundCode(fund.getCode())
            .accountType("UNITS")
            .accountName("Total outstanding units:")
            .quantity(new BigDecimal("100000.000"))
            .marketPrice(new BigDecimal(navPerUnit))
            .marketValue(new BigDecimal(aum))
            .build();

    when(navReportRepository.findLatestByNavDateAndFundCode(navDate, fund.getCode()))
        .thenReturn(List.of(navRow, unitsRow));
  }

  private void stubFundValue(String key, LocalDate date, String value) {
    when(fundValueProvider.getValueForDate(key, date))
        .thenReturn(
            Optional.of(new FundValue(key, date, new BigDecimal(value), "TULEVA", CALC_TIME)));
  }
}
