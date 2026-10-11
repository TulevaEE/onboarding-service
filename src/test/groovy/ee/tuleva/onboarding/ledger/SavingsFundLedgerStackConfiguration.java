package ee.tuleva.onboarding.ledger;

import ee.tuleva.onboarding.time.ClockConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import({
  LedgerService.class,
  LedgerAccountService.class,
  LedgerPartyService.class,
  LedgerTransactionService.class,
  UserUnitBalanceGuard.class,
  SavingsFundLedgerAccounts.class,
  LegacyTransferTypes.class,
  RedemptionLedgerRecorder.class,
  UnattributedPaymentLedgerRecorder.class,
  UnitTransferLedgerRecorder.class,
  SavingsFundLedger.class,
  ClockConfig.class
})
public class SavingsFundLedgerStackConfiguration {}
