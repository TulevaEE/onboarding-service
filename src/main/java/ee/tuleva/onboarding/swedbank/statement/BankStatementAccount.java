package ee.tuleva.onboarding.swedbank.statement;

import ee.tuleva.onboarding.banking.iso20022.camt052.AccountReport11;
import ee.tuleva.onboarding.banking.iso20022.camt052.GenericOrganisationIdentification1;
import ee.tuleva.onboarding.banking.iso20022.camt052.OrganisationIdentification4;
import ee.tuleva.onboarding.banking.iso20022.camt052.Party6Choice;
import ee.tuleva.onboarding.banking.iso20022.camt052.PartyIdentification32;
import ee.tuleva.onboarding.banking.iso20022.camt053.AccountStatement2;
import java.util.List;
import java.util.Optional;

public record BankStatementAccount(
    String iban, String accountHolderName, String accountHolderIdCode) {

  static BankStatementAccount from(AccountReport11 report) {
    var iban = Require.notNullOrBlank(report.getAcct().getId().getIBAN(), "account IBAN");

    // Extract account holder information from Ownr section
    var owner = Require.notNull(report.getAcct().getOwnr(), "account owner");
    var accountHolderName = Require.notNullOrBlank(owner.getNm(), "account holder name");

    // Extract account holder ID code (organization or private)
    var accountHolderIdCodes =
        Optional.of(owner)
            .map(PartyIdentification32::getId)
            .map(Party6Choice::getOrgId)
            .map(OrganisationIdentification4::getOthr)
            .map(
                others ->
                    others.stream()
                        .map(GenericOrganisationIdentification1::getId)
                        .filter(id -> id != null && !id.isBlank())
                        .toList())
            .orElseGet(List::of);

    var accountHolderIdCode = Require.exactlyOne(accountHolderIdCodes, "account holder ID code");

    return new BankStatementAccount(iban, accountHolderName, accountHolderIdCode);
  }

  static BankStatementAccount from(AccountStatement2 statement) {
    var iban = Require.notNullOrBlank(statement.getAcct().getId().getIBAN(), "account IBAN");

    // Extract account holder information from Ownr section
    var owner = Require.notNull(statement.getAcct().getOwnr(), "account owner");
    var accountHolderName = Require.notNullOrBlank(owner.getNm(), "account holder name");

    // Extract account holder ID code (organization or private)
    var accountHolderIdCodes =
        Optional.of(owner)
            .map(ee.tuleva.onboarding.banking.iso20022.camt053.PartyIdentification32::getId)
            .map(ee.tuleva.onboarding.banking.iso20022.camt053.Party6Choice::getOrgId)
            .map(ee.tuleva.onboarding.banking.iso20022.camt053.OrganisationIdentification4::getOthr)
            .map(
                others ->
                    others.stream()
                        .map(
                            ee.tuleva.onboarding.banking.iso20022.camt053
                                    .GenericOrganisationIdentification1
                                ::getId)
                        .filter(id -> id != null && !id.isBlank())
                        .toList())
            .orElseGet(List::of);

    var accountHolderIdCode = Require.exactlyOne(accountHolderIdCodes, "account holder ID code");

    return new BankStatementAccount(iban, accountHolderName, accountHolderIdCode);
  }
}
