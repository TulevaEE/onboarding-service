package ee.tuleva.onboarding.banking.statement;

import ee.tuleva.onboarding.banking.converter.ZonedDateTimeToXmlGregorianCalendarConverter;
import ee.tuleva.onboarding.banking.iso20022.camt052.AccountIdentification4Choice;
import ee.tuleva.onboarding.banking.iso20022.camt052.AccountReport11;
import ee.tuleva.onboarding.banking.iso20022.camt052.ActiveOrHistoricCurrencyAndAmount;
import ee.tuleva.onboarding.banking.iso20022.camt052.BalanceType12;
import ee.tuleva.onboarding.banking.iso20022.camt052.BalanceType12Code;
import ee.tuleva.onboarding.banking.iso20022.camt052.BalanceType5Choice;
import ee.tuleva.onboarding.banking.iso20022.camt052.CashAccount20;
import ee.tuleva.onboarding.banking.iso20022.camt052.CashBalance3;
import ee.tuleva.onboarding.banking.iso20022.camt052.CreditDebitCode;
import ee.tuleva.onboarding.banking.iso20022.camt052.DateAndDateTimeChoice;
import ee.tuleva.onboarding.banking.iso20022.camt052.DateTimePeriodDetails;
import ee.tuleva.onboarding.banking.iso20022.camt052.EntryDetails1;
import ee.tuleva.onboarding.banking.iso20022.camt052.EntryTransaction2;
import ee.tuleva.onboarding.banking.iso20022.camt052.GenericOrganisationIdentification1;
import ee.tuleva.onboarding.banking.iso20022.camt052.OrganisationIdentification4;
import ee.tuleva.onboarding.banking.iso20022.camt052.Party6Choice;
import ee.tuleva.onboarding.banking.iso20022.camt052.PartyIdentification32;
import ee.tuleva.onboarding.banking.iso20022.camt052.RemittanceInformation5;
import ee.tuleva.onboarding.banking.iso20022.camt052.ReportEntry2;
import ee.tuleva.onboarding.banking.iso20022.camt052.TotalTransactions2;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Builds minimal camt.052 (AccountReport11) object graphs for BankStatement unit tests. */
final class Camt052Fixtures {

  private Camt052Fixtures() {}

  static AccountReport11 accountReport(
      CashAccount20 account,
      List<CashBalance3> balances,
      List<ReportEntry2> entries,
      @Nullable TotalTransactions2 summary,
      ZonedDateTime toDateTime) {
    var period = new DateTimePeriodDetails();
    period.setToDtTm(new ZonedDateTimeToXmlGregorianCalendarConverter().convert(toDateTime));

    var report = new AccountReport11();
    report.setAcct(account);
    report.setFrToDt(period);
    report.getBal().addAll(balances);
    report.getNtry().addAll(entries);
    report.setTxsSummry(summary);
    return report;
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

  static ReportEntry2 creditEntry(String amount) {
    var amt = new ActiveOrHistoricCurrencyAndAmount();
    amt.setValue(new BigDecimal(amount));
    amt.setCcy("EUR");

    var remittanceInfo = new RemittanceInformation5();
    remittanceInfo.getUstrd().add("test payment");

    var transaction = new EntryTransaction2();
    transaction.setRmtInf(remittanceInfo);

    var entryDetails = new EntryDetails1();
    entryDetails.getTxDtls().add(transaction);

    var entry = new ReportEntry2();
    entry.setAmt(amt);
    entry.setCdtDbtInd(CreditDebitCode.CRDT);
    entry.setNtryRef("EXT-1");
    entry.getNtryDtls().add(entryDetails);
    return entry;
  }

  static CashBalance3 balance(BalanceType12Code code, LocalDate date, String amount) {
    return balance(code, date, amount, CreditDebitCode.CRDT);
  }

  static CashBalance3 balance(
      BalanceType12Code code, LocalDate date, String amount, CreditDebitCode creditOrDebit) {
    var amt = new ActiveOrHistoricCurrencyAndAmount();
    amt.setValue(new BigDecimal(amount));
    amt.setCcy("EUR");

    var typeChoice = new BalanceType5Choice();
    typeChoice.setCd(code);
    var type = new BalanceType12();
    type.setCdOrPrtry(typeChoice);

    var dateChoice = new DateAndDateTimeChoice();
    dateChoice.setDt(date);

    var balance = new CashBalance3();
    balance.setTp(type);
    balance.setAmt(amt);
    balance.setCdtDbtInd(creditOrDebit);
    balance.setDt(dateChoice);
    return balance;
  }
}
