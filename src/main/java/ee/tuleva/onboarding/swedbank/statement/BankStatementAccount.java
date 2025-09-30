package ee.tuleva.onboarding.swedbank.statement;

import static ee.tuleva.onboarding.swedbank.statement.BankStatementAccount.BankAccountType.DEPOSIT_EUR;

import ee.swedbank.gateway.iso.response.report.*;
import ee.swedbank.gateway.iso.response.statement.AccountStatement2;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public record BankStatementAccount(
    String iban, @Nullable String accountHolderName, List<String> accountHolderIdCodes) {

  public enum BankAccountType {
    DEPOSIT_EUR,
    WITHDRAWAL_EUR,
    FUND_INVESTMENT_EUR
  }

  public BankAccountType getBankAccountType() {
    return DEPOSIT_EUR;
  }

  public static BankStatementAccount from(AccountReport11 report) {
    var iban = report.getAcct().getId().getIBAN();

    // Extract account holder information from Ownr section
    var owner = report.getAcct().getOwnr();
    var accountHolderName = owner != null ? owner.getNm() : null;

    // Extract account holder ID code (organization or private)
    var accountHolderIdCodes =
        Optional.ofNullable(owner)
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

    return new BankStatementAccount(iban, accountHolderName, accountHolderIdCodes);
  }

  public static BankStatementAccount from(AccountStatement2 statement) {
    return new BankStatementAccount(statement.getAcct().getId().getIBAN(), "TODO", List.of());
  }
}
