package ee.tuleva.onboarding.banking.statement;

import ee.tuleva.onboarding.banking.converter.ZonedDateTimeToXmlGregorianCalendarConverter;
import ee.tuleva.onboarding.banking.iso20022.camt053.AccountIdentification4Choice;
import ee.tuleva.onboarding.banking.iso20022.camt053.AccountStatement2;
import ee.tuleva.onboarding.banking.iso20022.camt053.CashAccount20;
import ee.tuleva.onboarding.banking.iso20022.camt053.CashBalance3;
import ee.tuleva.onboarding.banking.iso20022.camt053.DateTimePeriodDetails;
import ee.tuleva.onboarding.banking.iso20022.camt053.GenericOrganisationIdentification1;
import ee.tuleva.onboarding.banking.iso20022.camt053.OrganisationIdentification4;
import ee.tuleva.onboarding.banking.iso20022.camt053.Party6Choice;
import ee.tuleva.onboarding.banking.iso20022.camt053.PartyIdentification32;
import ee.tuleva.onboarding.banking.iso20022.camt053.ReportEntry2;
import ee.tuleva.onboarding.banking.iso20022.camt053.TotalTransactions2;
import java.time.ZonedDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class Camt053Fixtures {

  private Camt053Fixtures() {}

  static AccountStatement2 accountStatement(
      CashAccount20 account,
      List<CashBalance3> balances,
      List<ReportEntry2> entries,
      @Nullable TotalTransactions2 summary,
      @Nullable ZonedDateTime fromDateTime,
      @Nullable ZonedDateTime toDateTime) {
    var converter = new ZonedDateTimeToXmlGregorianCalendarConverter();
    var period = new DateTimePeriodDetails();
    period.setFrDtTm(fromDateTime == null ? null : converter.convert(fromDateTime));
    period.setToDtTm(toDateTime == null ? null : converter.convert(toDateTime));

    var statement = new AccountStatement2();
    statement.setAcct(account);
    statement.setFrToDt(period);
    statement.getBal().addAll(balances);
    statement.getNtry().addAll(entries);
    statement.setTxsSummry(summary);
    return statement;
  }

  static CashAccount20 account(String iban, String holderName, List<String> holderIdCodes) {
    var accountId = new AccountIdentification4Choice();
    accountId.setIBAN(iban);

    var organisationId = new OrganisationIdentification4();
    for (String code : holderIdCodes) {
      var generic = new GenericOrganisationIdentification1();
      generic.setId(code);
      organisationId.getOthr().add(generic);
    }
    var partyId = new Party6Choice();
    partyId.setOrgId(organisationId);

    var owner = new PartyIdentification32();
    owner.setNm(holderName);
    owner.setId(partyId);

    var account = new CashAccount20();
    account.setId(accountId);
    account.setOwnr(owner);
    return account;
  }
}
