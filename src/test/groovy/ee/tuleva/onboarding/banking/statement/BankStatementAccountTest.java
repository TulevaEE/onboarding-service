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
    var accountId =
        new ee.tuleva.onboarding.banking.iso20022.camt053.AccountIdentification4Choice();
    accountId.setIBAN("EE001234567890123456");

    var blank =
        new ee.tuleva.onboarding.banking.iso20022.camt053.GenericOrganisationIdentification1();
    blank.setId("");
    var real =
        new ee.tuleva.onboarding.banking.iso20022.camt053.GenericOrganisationIdentification1();
    real.setId("10060701");
    var organisationId =
        new ee.tuleva.onboarding.banking.iso20022.camt053.OrganisationIdentification4();
    organisationId.getOthr().add(blank);
    organisationId.getOthr().add(real);
    var partyId = new ee.tuleva.onboarding.banking.iso20022.camt053.Party6Choice();
    partyId.setOrgId(organisationId);

    var owner = new ee.tuleva.onboarding.banking.iso20022.camt053.PartyIdentification32();
    owner.setNm("Acme OÜ");
    owner.setId(partyId);

    var account = new ee.tuleva.onboarding.banking.iso20022.camt053.CashAccount20();
    account.setId(accountId);
    account.setOwnr(owner);

    var statement = new ee.tuleva.onboarding.banking.iso20022.camt053.AccountStatement2();
    statement.setAcct(account);

    var result = BankStatementAccount.from(statement);

    assertThat(result)
        .isEqualTo(new BankStatementAccount("EE001234567890123456", "Acme OÜ", "10060701"));
  }
}
