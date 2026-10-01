package ee.tuleva.onboarding.ledger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import({
  GeneralLedger.class,
  GeneralLedgerAccounts.class,
  JournalEntryWriter.class,
  LedgerAccountService.class,
  LedgerTransactionService.class,
  UserUnitBalanceGuard.class,
  GeneralLedgerRows.class
})
public class GeneralLedgerStackConfiguration {}
