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

    when(navLedgerRepository.getSystemAccountBalance(SECURITIES_UNITS.getAccountName(ISIN_A)))
        .thenReturn(new BigDecimal("500"));
    when(navLedgerRepository.getSystemAccountBalance(CASH_POSITION.getAccountName()))
        .thenReturn(ZERO);
    when(navLedgerRepository.getSystemAccountBalance(TRADE_RECEIVABLES.getAccountName()))
        .thenReturn(ZERO);
    when(navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.getAccountName()))
        .thenReturn(ZERO);

    when(navLedgerRepository.getSecuritiesUnitBalances())
        .thenReturn(
            Map.of(
                ISIN_A, new BigDecimal("500"),
                ISIN_B, new BigDecimal("18430.331")));

    service.recordPositionsToLedger(TKF100, DATE);

    verify(navPositionLedger)
        .recordPositions(
            TKF100.name(),
            DATE,
            Map.of(ISIN_A, new BigDecimal("500"), ISIN_B, new BigDecimal("-18430.331")),
            ZERO,
            ZERO,
            ZERO);
  }
}
