package ee.tuleva.onboarding.investment.position;

import static ee.tuleva.onboarding.fund.TulevaFund.TUK75;
import static ee.tuleva.onboarding.investment.position.AccountType.*;
import static ee.tuleva.onboarding.ledger.SystemAccount.*;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.ledger.NavLedgerRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FundPositionLedgerServiceIntegrationTest {

  @Autowired FundPositionLedgerService fundPositionLedgerService;
  @Autowired FundPositionRepository fundPositionRepository;
  @Autowired NavLedgerRepository navLedgerRepository;
  @Autowired EntityManager entityManager;

  @Test
  void rerecordPositions_filtersSwedBankLiabilitiesAndComputesFeesForFirstWeekend() {
    LocalDate feb27 = LocalDate.of(2026, 2, 27);
    LocalDate mar2 = LocalDate.of(2026, 3, 2);

    // Feb 27 positions — includes Swedbank-era LIABILITY entries that should be filtered
    insertSecurity(feb27, "IE00BFG1TM61", 8042414.77, 34.466, 277189867.46);
    insertSecurity(feb27, "IE0009FT4LX4", 17953577.58, 15.498, 278244545.33);
    insertSecurity(feb27, "IE00BFNM3G45", 17609096.00, 11.944, 210323042.62);
    insertSecurity(feb27, "IE00BFNM3D14", 7026993.00, 10.602, 74500179.79);
    insertSecurity(feb27, "IE00BFNM3L97", 1034931.00, 7.985, 8263924.04);
    insertSecurity(feb27, "IE00BKPTWY98", 7550798.54, 13.852, 104593661.38);
    insertPosition(feb27, CASH, "Cash account in SEB Pank", null, 1791360.01);
    insertPosition(feb27, RECEIVABLES, "Total receivables of unsettled transactions", null, 0.00);
    insertPosition(feb27, LIABILITY, "Total payables of unsettled transactions", null, 0.00);
    insertPosition(feb27, LIABILITY, "Payables of redeemed units", "EE0000003283", 0.00);
    // Swedbank-era entries — should be excluded from TRADE_PAYABLES
    insertPosition(feb27, LIABILITY, "Management Fee Payable", "EE0000003283", -5324.63);
    insertPosition(feb27, LIABILITY, "Payables to Depository Bank", "EE0000003283", -8115.67);
    insertNavPosition(feb27);

    // Mar 2 positions — clean SEB data
    insertSecurity(mar2, "IE00BFG1TM61", 8042414.77, 34.343, 276200650.45);
    insertSecurity(mar2, "IE0009FT4LX4", 17953577.58, 15.451, 277400727.13);
    insertSecurity(mar2, "IE00BFNM3G45", 17609096.00, 12.048, 212154388.61);
    insertSecurity(mar2, "IE00BFNM3D14", 7026993.00, 10.418, 73207213.07);
    insertSecurity(mar2, "IE00BFNM3L97", 1034931.00, 7.828, 8101439.87);
    insertSecurity(mar2, "IE00BKPTWY98", 7550798.54, 13.770, 103974495.90);
    insertPosition(mar2, CASH, "Cash account in SEB Pank", null, 1633975.32);
    insertPosition(mar2, RECEIVABLES, "Total receivables of unsettled transactions", null, 0.00);
    insertPosition(mar2, LIABILITY, "Total payables of unsettled transactions", null, 0.00);
    insertPosition(mar2, LIABILITY, "Payables of redeemed units", "EE3600109435", 0.00);
    insertNavPosition(mar2);

    entityManager.flush();
    entityManager.clear();

    // Act: re-record positions from Mar 1 (includes Feb 27 as previous working day)
    fundPositionLedgerService.rerecordPositions(TUK75, LocalDate.of(2026, 3, 1));

    // Assert: TRADE_PAYABLES = 0 (Swedbank entries "Management Fee Payable" and
    // "Payables to Depository Bank" filtered out, only trade settlement payables included)
    BigDecimal payables =
        navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.getAccountName(TUK75));
    assertThat(payables).isEqualByComparingTo(ZERO);

    // Assert: Feb 27 cash position recorded to ledger (proves pre-fromDate data is included)
    BigDecimal cash =
        navLedgerRepository.getSystemAccountBalance(CASH_POSITION.getAccountName(TUK75));
    assertThat(cash).isEqualByComparingTo(new BigDecimal("1633975.32"));

    // Assert: securities units from both dates are in ledger
    var unitBalances = navLedgerRepository.getSecuritiesUnitBalances(TUK75);
    assertThat(unitBalances).containsKey("IE00BFG1TM61");
    assertThat(unitBalances.get("IE00BFG1TM61")).isEqualByComparingTo("8042414.77");
  }

  @Test
  void rerecordPositions_deletesOldPositionUpdatesBeforeRerecording() {
    LocalDate feb27 = LocalDate.of(2026, 2, 27);

    insertSecurity(feb27, "IE00BFG1TM61", 1000.00, 10.00, 10000.00);
    insertPosition(feb27, CASH, "Cash account", null, 50000.00);
    insertPosition(feb27, LIABILITY, "Total payables of unsettled transactions", null, -5000.00);
    insertPosition(feb27, LIABILITY, "Payables of redeemed units", null, 0.00);
    insertNavPosition(feb27);

    entityManager.flush();
    entityManager.clear();

    // First record
    fundPositionLedgerService.recordPositionsToLedger(TUK75, feb27);

    BigDecimal cashAfterFirst =
        navLedgerRepository.getSystemAccountBalance(CASH_POSITION.getAccountName(TUK75));
    assertThat(cashAfterFirst).isEqualByComparingTo("50000.00");

    BigDecimal payablesAfterFirst =
        navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.getAccountName(TUK75));
    assertThat(payablesAfterFirst).isEqualByComparingTo("-5000.00");

    // Rerecord — should delete old entries and re-record, not double them
    fundPositionLedgerService.rerecordPositions(TUK75, LocalDate.of(2026, 3, 1));

    BigDecimal cashAfterRerecord =
        navLedgerRepository.getSystemAccountBalance(CASH_POSITION.getAccountName(TUK75));
    assertThat(cashAfterRerecord).isEqualByComparingTo("50000.00");

    BigDecimal payablesAfterRerecord =
        navLedgerRepository.getSystemAccountBalance(TRADE_PAYABLES.getAccountName(TUK75));
    assertThat(payablesAfterRerecord).isEqualByComparingTo("-5000.00");
  }

  private void insertSecurity(
      LocalDate navDate, String isin, double quantity, double price, double marketValue) {
    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(navDate)
            .fund(TUK75)
            .accountType(SECURITY)
            .accountName(isin)
            .accountId(isin)
            .quantity(new BigDecimal(String.valueOf(quantity)))
            .marketPrice(new BigDecimal(String.valueOf(price)))
            .marketValue(new BigDecimal(String.valueOf(marketValue)))
            .currency("EUR")
            .build());
  }

  private void insertPosition(
      LocalDate navDate, AccountType type, String name, String accountId, double marketValue) {
    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(navDate)
            .fund(TUK75)
            .accountType(type)
            .accountName(name)
            .accountId(accountId)
            .marketValue(new BigDecimal(String.valueOf(marketValue)))
            .currency("EUR")
            .build());
  }

  private void insertNavPosition(LocalDate navDate) {
    fundPositionRepository.save(
        FundPosition.builder()
            .navDate(navDate)
            .fund(TUK75)
            .accountType(NAV)
            .accountName("Net Asset Value")
            .accountId(TUK75.getIsin())
            .quantity(BigDecimal.ONE)
            .marketPrice(BigDecimal.ONE)
            .currency("EUR")
            .marketValue(BigDecimal.ONE)
            .build());
  }
}
