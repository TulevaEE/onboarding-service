package ee.tuleva.onboarding.swedbank.statement;

import static ee.tuleva.onboarding.swedbank.statement.BankStatementAccount.BankAccountType.DEPOSIT_EUR;

import ee.swedbank.gateway.iso.response.report.*;
import ee.swedbank.gateway.iso.response.statement.AccountStatement2;
import ee.swedbank.gateway.iso.response.report.AccountReport11;
import ee.swedbank.gateway.iso.response.report.GenericOrganisationIdentification1;
import ee.swedbank.gateway.iso.response.report.OrganisationIdentification4;
import ee.swedbank.gateway.iso.response.report.Party6Choice;
import ee.swedbank.gateway.iso.response.report.PartyIdentification32;
import java.util.List;
import java.util.Optional;

public record BankStatementAccount(
    String iban, String accountHolderName, String accountHolderIdCode) {

  public enum BankAccountType {
    DEPOSIT_EUR,
    WITHDRAWAL_EUR,
    FUND_INVESTMENT_EUR
  }

  public BankAccountType getBankAccountType() {
    return DEPOSIT_EUR;
  }

  static BankStatementAccount from(AccountReport11 report) {
    var iban = report.getAcct().getId().getIBAN();

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
    var iban = statement.getAcct().getId().getIBAN();

    // Extract account holder information from Ownr section
    var owner = Require.notNull(statement.getAcct().getOwnr(), "account owner");
    var accountHolderName = Require.notNullOrBlank(owner.getNm(), "account holder name");

    // Extract account holder ID code (organization or private)
    var accountHolderIdCodes =
        Optional.of(owner)
            .map(ee.swedbank.gateway.iso.response.statement.PartyIdentification32::getId)
            .map(ee.swedbank.gateway.iso.response.statement.Party6Choice::getOrgId)
            .map(ee.swedbank.gateway.iso.response.statement.OrganisationIdentification4::getOthr)
            .map(
                others ->
                    others.stream()
                        .map(
                            ee.swedbank.gateway.iso.response.statement
                                    .GenericOrganisationIdentification1
                                ::getId)
                        .filter(id -> id != null && !id.isBlank())
                        .toList())
            .orElseGet(List::of);

    var accountHolderIdCode = Require.exactlyOne(accountHolderIdCodes, "account holder ID code");

    return new BankStatementAccount(iban, accountHolderName, accountHolderIdCode);
  }
}
