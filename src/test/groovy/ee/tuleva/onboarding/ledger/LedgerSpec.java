package ee.tuleva.onboarding.ledger;

import static ee.tuleva.onboarding.currency.Currency.EUR;
import static java.math.BigDecimal.ZERO;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerSpec {

  @Test
  void canCreateEntries() {
    var transactionId = UUID.randomUUID();
    var createdTime = Instant.now();
    var accountId = UUID.randomUUID();
    BigDecimal amount = new BigDecimal("123.45");
    BigDecimal amount2 = amount.negate();

    var entry1 =
        new LedgerEntry(UUID.randomUUID(), amount, EUR, createdTime, transactionId, accountId);
    var entry2 =
        new LedgerEntry(UUID.randomUUID(), amount2, EUR, createdTime, transactionId, accountId);

    var transaction = new LedgerTransaction(transactionId, List.of(entry1, entry2), createdTime);

    var account1 = new LedgerAccount(accountId, List.of(entry1), createdTime);
    var account2 = new LedgerAccount(accountId, List.of(entry2), createdTime);

    assertThat(account1.balance()).isEqualTo(amount);
    assertThat(account2.balance()).isEqualTo(amount2);
    assertThat(transaction.sum()).isEqualByComparingTo(ZERO);
  }
}
