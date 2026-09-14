package ee.tuleva.onboarding.banking.statement;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.iso20022.camt052.AccountReport11;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BankStatementAccountTest {

  @Test
  void from_accountReport_filtersBlankHolderIdCodes() {
    var account =
        Camt052Fixtures.account("EE001234567890123456", "Acme OÜ", List.of("", "10060701"));
    AccountReport11 report =
        Camt052Fixtures.accountReport(account, List.of(), List.of(), null, ZonedDateTime.now());

    var result = BankStatementAccount.from(report);

    assertThat(result)
        .isEqualTo(new BankStatementAccount("EE001234567890123456", "Acme OÜ", "10060701"));
  }

  @Test
  void from_accountStatement_filtersBlankHolderIdCodes() {
    var account =
        Camt053Fixtures.account("EE001234567890123456", "Acme OÜ", List.of("", "10060701"));
    var statement = new ee.tuleva.onboarding.banking.iso20022.camt053.AccountStatement2();
    statement.setAcct(account);

    var result = BankStatementAccount.from(statement);

    assertThat(result)
        .isEqualTo(new BankStatementAccount("EE001234567890123456", "Acme OÜ", "10060701"));
  }
}
