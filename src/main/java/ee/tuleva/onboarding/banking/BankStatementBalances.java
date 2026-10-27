package ee.tuleva.onboarding.banking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface BankStatementBalances {

  Optional<BigDecimal> closingBalance(BankAccount account, LocalDate day);

  boolean isInsideAStatementClosingLater(BankAccount account, LocalDate day);
}
