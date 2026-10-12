package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.ledger.LedgerParty.PartyType.LEGAL_ENTITY;
import static ee.tuleva.onboarding.ledger.LedgerTransaction.TransactionType.FUND_SUBSCRIPTION;
import static ee.tuleva.onboarding.ledger.SystemAccount.FUND_UNITS_OUTSTANDING;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS;
import static ee.tuleva.onboarding.ledger.UserAccount.FUND_UNITS_RESERVED;
import static ee.tuleva.onboarding.tulevafund.TulevaFund.TKF100;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({
  LedgerService.class,
  LedgerAccountService.class,
  LedgerPartyService.class,
  SavingsFundUnits.class
})
class SavingsFundUnitsIntegrationTest {

  private static final String REGISTRY_CODE = "10000000";
  private static final Instant START_OF_OCTOBER = Instant.parse("2026-09-30T21:00:00Z");

  @Autowired private LedgerService ledgerService;
  @Autowired private LedgerTransactionRepository ledgerTransactionRepository;
  @Autowired private SavingsFundUnits savingsFundUnits;
  @Autowired private TestEntityManager entityManager;

  @Test
  void aCompanyHoldsItsFreeAndItsReservedUnitsAtTheCutoff() {
    issueUnits(FUND_UNITS, "200000.00000", START_OF_OCTOBER.minusSeconds(86_400));
    issueUnits(FUND_UNITS_RESERVED, "901.49000", START_OF_OCTOBER.minusSeconds(3_600));

    assertThat(savingsFundUnits.unitsHeldAt(REGISTRY_CODE, START_OF_OCTOBER))
        .hasValueSatisfying(units -> assertThat(units).isEqualByComparingTo("200901.49"));
  }

  @Test
  void unitsIssuedAfterTheCutoffAreNotYetHeld() {
    issueUnits(FUND_UNITS, "1000.00000", START_OF_OCTOBER.minusSeconds(3_600));
    issueUnits(FUND_UNITS, "500.00000", START_OF_OCTOBER.plusSeconds(3_600));

    assertThat(savingsFundUnits.unitsHeldAt(REGISTRY_CODE, START_OF_OCTOBER))
        .hasValueSatisfying(units -> assertThat(units).isEqualByComparingTo("1000"));
  }

  @Test
  void aCompanyWhoseUnitAccountIsEmptyAtTheCutoffHoldsZeroUnits() {
    issueUnits(FUND_UNITS, "1000.00000", START_OF_OCTOBER.plusSeconds(3_600));

    assertThat(savingsFundUnits.unitsHeldAt(REGISTRY_CODE, START_OF_OCTOBER))
        .hasValueSatisfying(units -> assertThat(units).isEqualByComparingTo("0"));
  }

  @Test
  void aCompanyWithoutUnitAccountsHasNoHoldingToShow() {
    assertThat(savingsFundUnits.unitsHeldAt(REGISTRY_CODE, START_OF_OCTOBER)).isEmpty();
  }

  private void issueUnits(UserAccount account, String units, Instant date) {
    var holder = ledgerService.getPartyAccount(REGISTRY_CODE, LEGAL_ENTITY, account);
    var outstanding = ledgerService.getSystemAccount(FUND_UNITS_OUTSTANDING, TKF100);
    var transaction =
        LedgerTransaction.builder()
            .transactionType(FUND_SUBSCRIPTION)
            .transactionDate(date)
            .metadata(Map.of())
            .build();
    transaction.addEntry(holder, new BigDecimal(units).negate());
    transaction.addEntry(outstanding, new BigDecimal(units));
    ledgerTransactionRepository.save(transaction);
    entityManager.flush();
    entityManager.clear();
  }
}
