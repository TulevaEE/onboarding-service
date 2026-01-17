package ee.tuleva.onboarding.banking.statement;

import static ee.tuleva.onboarding.banking.iso20022.camt060.QueryType3Code.ALLL;

import ee.tuleva.onboarding.banking.iso20022.camt060.AccountIdentification4Choice;
import ee.tuleva.onboarding.banking.iso20022.camt060.AccountReportingRequestV03;
import ee.tuleva.onboarding.banking.iso20022.camt060.CashAccount24;
import ee.tuleva.onboarding.banking.iso20022.camt060.DatePeriodDetails1;
import ee.tuleva.onboarding.banking.iso20022.camt060.Document;
import ee.tuleva.onboarding.banking.iso20022.camt060.GroupHeader59;
import ee.tuleva.onboarding.banking.iso20022.camt060.ObjectFactory;
import ee.tuleva.onboarding.banking.iso20022.camt060.Party12Choice;
import ee.tuleva.onboarding.banking.iso20022.camt060.PartyIdentification43;
import ee.tuleva.onboarding.banking.iso20022.camt060.ReportingPeriod1;
import ee.tuleva.onboarding.banking.iso20022.camt060.ReportingRequest3;
import ee.tuleva.onboarding.banking.iso20022.camt060.TimePeriodDetails1;
import jakarta.xml.bind.JAXBElement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import javax.xml.datatype.XMLGregorianCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StatementRequestMessageGenerator {

  private static final String INTRA_DAY_MESSAGE_TYPE = "camt.052.001.02";
  private static final String HISTORIC_MESSAGE_TYPE = "camt.053.001.02";

  private final Clock clock;
  private final Converter<LocalDate, XMLGregorianCalendar> dateConverter;
  private final Converter<ZonedDateTime, XMLGregorianCalendar> timeConverter;

  public JAXBElement<Document> generateIntraDayReportRequest(
      String accountIban, UUID messageId, ZoneId timeZone) {
    return generateReportRequest(
        accountIban,
        messageId,
        INTRA_DAY_MESSAGE_TYPE,
        LocalDate.now(clock),
        LocalDate.now(clock),
        timeZone);
  }

  public JAXBElement<Document> generateHistoricReportRequest(
      String accountIban, UUID messageId, LocalDate fromDate, LocalDate toDate, ZoneId timeZone) {
    return generateReportRequest(
        accountIban, messageId, HISTORIC_MESSAGE_TYPE, fromDate, toDate, timeZone);
  }

  private JAXBElement<Document> generateReportRequest(
      String accountIban,
      UUID messageId,
      String messageType,
      LocalDate fromDate,
      LocalDate toDate,
      ZoneId timeZone) {

    var accountReportingRequest = new AccountReportingRequestV03();
    accountReportingRequest.setGrpHdr(createGroupHeader(messageId));
    accountReportingRequest
        .getRptgReq()
        .add(
            createReportingRequest(
                accountIban, messageId, messageType, fromDate, toDate, timeZone));

    var document = new Document();
    document.setAcctRptgReq(accountReportingRequest);

    return new ObjectFactory().createDocument(document);
  }

  private GroupHeader59 createGroupHeader(UUID messageId) {
    var groupHeader = new GroupHeader59();
    groupHeader.setMsgId(serializeId(messageId));
    groupHeader.setCreDtTm(timeConverter.convert(ZonedDateTime.now(clock)));
    return groupHeader;
  }

  private ReportingRequest3 createReportingRequest(
      String accountIban,
      UUID messageId,
      String messageType,
      LocalDate fromDate,
      LocalDate toDate,
      ZoneId timeZone) {

    var reportingRequest = new ReportingRequest3();
    reportingRequest.setId(serializeId(messageId));
    reportingRequest.setReqdMsgNmId(messageType);
    reportingRequest.setAcct(createCashAccount(accountIban));
    reportingRequest.setAcctOwnr(createAccountOwner());
    reportingRequest.setRptgPrd(createReportingPeriod(fromDate, toDate, timeZone));
    return reportingRequest;
  }

  private CashAccount24 createCashAccount(String accountIban) {
    var accountIdentification = new AccountIdentification4Choice();
    accountIdentification.setIBAN(accountIban);

    var cashAccount = new CashAccount24();
    cashAccount.setId(accountIdentification);
    return cashAccount;
  }

  private Party12Choice createAccountOwner() {
    var partyChoice = new Party12Choice();
    partyChoice.setPty(new PartyIdentification43());
    return partyChoice;
  }

  private ReportingPeriod1 createReportingPeriod(
      LocalDate fromDate, LocalDate toDate, ZoneId timeZone) {
    var period = new ReportingPeriod1();
    period.setFrToDt(createDatePeriod(fromDate, toDate));
    period.setTp(ALLL);
    period.setFrToTm(createTimePeriod(fromDate, toDate, timeZone));
    return period;
  }

  private DatePeriodDetails1 createDatePeriod(LocalDate fromDate, LocalDate toDate) {
    var datePeriod = new DatePeriodDetails1();
    datePeriod.setFrDt(dateConverter.convert(fromDate));
    datePeriod.setToDt(dateConverter.convert(toDate));
    return datePeriod;
  }

  private TimePeriodDetails1 createTimePeriod(
      LocalDate fromDate, LocalDate toDate, ZoneId timeZone) {
    var timePeriod = new TimePeriodDetails1();
    timePeriod.setFrTm(timeConverter.convert(fromDate.atStartOfDay(timeZone)));
    timePeriod.setToTm(timeConverter.convert(toDate.atStartOfDay(timeZone).with(LocalTime.MAX)));
    return timePeriod;
  }

  private static String serializeId(UUID id) {
    return id.toString().replace("-", "");
  }
}
