package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.fund.TulevaFund.TKF100;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.BigDecimal.ZERO;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import ee.tuleva.onboarding.ledger.NavPositionLedger;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FundPositionLedgerServiceTest {

  @Mock private FundPositionRepository fundPositionRepository;
  @Mock private NavPositionLedger navPositionLedger;
  @Mock private NavLedgerRepository navLedgerRepository;

  @InjectMocks private FundPositionLedgerService service;

  private static final LocalDate DATE = LocalDate.of(2026, 2, 5);
  private static final String ISIN_A = "IE00BFG1TM61";
  private static final String ISIN_B = "IE00BMDBMY19";

  @Test
  void excludesPendingSubscriptionsFromReceivables() {
    var tradeReceivable =
        FundPosition.builder()
            .navDate(DATE)
            .fund(TKF100)
            .accountType(RECEIVABLES)
            .accountName("Total receivables of unsettled transactions")
            .marketValue(new BigDecimal("500.00"))
            .build();
    var pendingSubscription =
        FundPosition.builder()
            .navDate(DATE)
            .fund(TKF100)
            .accountType(RECEIVABLES)
            .accountName("Receivables of outstanding units")
            .accountId(TKF100.getIsin())
            .marketValue(new BigDecimal("242059.62"))
            .build();

    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, TKF100, SECURITY))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, TKF100, CASH))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, TKF100, RECEIVABLES))
        .thenReturn(List.of(tradeReceivable, pendingSubscription));
    when(fundPositionRepository.findByNavDateAndFundAndAccountTypeAndAccountId(
            DATE, TKF100, RECEIVABLES, TKF100.getIsin()))
        .thenReturn(Optional.of(pendingSubscription));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, TKF100, LIABILITY))
        .thenReturn(List.of());

    when(navLedgerRepository.getSystemAccountBalance(CASH_POSITION.getAccountName(TKF100)))
        .thenReturn(ZERO);
    when(navLedgerRepository.getSystemAccountBalance(TRADE_RECEIVABLES.getAccountName(TKF100)))
        .thenReturn(ZERO);
    when(navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.getAccountName(TKF100)))
        .thenReturn(ZERO);
    when(navLedgerRepository.getSecuritiesUnitBalances(TKF100)).thenReturn(Map.of());

    service.recordPositionsToLedger(TKF100, DATE);

    verify(navPositionLedger)
        .recordPositions(TKF100, DATE, Map.of(), ZERO, new BigDecimal("500.00"), ZERO);
  }

  @Test
  void zerosOutSecuritiesMissingFromNewPositionReport() {
    var positionA =
        FundPosition.builder()
            .navDate(DATE)
            .fund(TKF100)
            .accountType(SECURITY)
            .accountName("iShares Fund")
            .accountId(ISIN_A)
            .quantity(new BigDecimal("1000"))
            .marketPrice(new BigDecimal("34.42"))
            .marketValue(new BigDecimal("34420"))
            .currency("EUR")
            .build();

    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, TKF100, SECURITY))
        .thenReturn(List.of(positionA));
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, TKF100, CASH))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, TKF100, RECEIVABLES))
        .thenReturn(List.of());
    when(fundPositionRepository.findByNavDateAndFundAndAccountType(DATE, TKF100, LIABILITY))
        .thenReturn(List.of());

    when(navLedgerRepository.getSystemAccountBalance(
            SECURITIES_UNITS.getAccountName(TKF100, ISIN_A)))
        .thenReturn(new BigDecimal("500"));
    when(navLedgerRepository.getSystemAccountBalance(CASH_POSITION.getAccountName(TKF100)))
        .thenReturn(ZERO);
    when(navLedgerRepository.getSystemAccountBalance(TRADE_RECEIVABLES.getAccountName(TKF100)))
        .thenReturn(ZERO);
    when(navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.getAccountName(TKF100)))
        .thenReturn(ZERO);

    when(navLedgerRepository.getSecuritiesUnitBalances(TKF100))
        .thenReturn(
            Map.of(
                ISIN_A, new BigDecimal("500"),
                ISIN_B, new BigDecimal("18430.331")));

    service.recordPositionsToLedger(TKF100, DATE);

    verify(navPositionLedger)
        .recordPositions(
            TKF100,
            DATE,
            Map.of(ISIN_A, new BigDecimal("500"), ISIN_B, new BigDecimal("-18430.331")),
            ZERO,
            ZERO,
            ZERO);
  }
}
