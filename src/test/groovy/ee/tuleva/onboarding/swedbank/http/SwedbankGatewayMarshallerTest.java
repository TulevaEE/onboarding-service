package ee.tuleva.onboarding.swedbank.http;

import static ee.tuleva.onboarding.swedbank.statement.BankStatementBalance.StatementBalanceType.CLOSE;
import static ee.tuleva.onboarding.swedbank.statement.BankStatementBalance.StatementBalanceType.OPEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import ee.swedbank.gateway.iso.response.Document;
import ee.tuleva.onboarding.swedbank.converter.LocalDateToXmlGregorianCalendarConverter;
import ee.tuleva.onboarding.swedbank.converter.ZonedDateTimeToXmlGregorianCalendarConverter;
import ee.tuleva.onboarding.swedbank.statement.BankStatement;
import ee.tuleva.onboarding.time.TestClockHolder;
import jakarta.xml.bind.JAXBElement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

public class SwedbankGatewayMarshallerTest {

  private SwedbankGatewayMarshaller swedbankGatewayMarshaller;

  private SwedbankGatewayClient swedbankGatewayClient;

  @BeforeEach
  void setUp() {
    this.swedbankGatewayMarshaller = new SwedbankGatewayMarshaller();
    this.swedbankGatewayClient =
        new SwedbankGatewayClient(
            "",
            "",
            "",
            TestClockHolder.clock,
            swedbankGatewayMarshaller,
            null,
            new LocalDateToXmlGregorianCalendarConverter(),
            new ZonedDateTimeToXmlGregorianCalendarConverter(),
            mock(RestTemplate.class));
  }

  @Test
  @DisplayName("marshals request class")
  public void marshalRequestClass() {
    var request =
        swedbankGatewayClient.getAccountStatementRequestEntity(
            "EE_TEST_IBAN", UUID.fromString("cdb18c2e-ad18-4f08-93a6-1f91492fb9f5"));

    var requestXml = swedbankGatewayMarshaller.marshalToString(request);

    assertEquals(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.060.001.03\"><AcctRptgReq><GrpHdr><MsgId>cdb18c2ead184f0893a61f91492fb9f5</MsgId><CreDtTm>2020-01-01T14:13:15.000Z</CreDtTm></GrpHdr><RptgReq><Id>cdb18c2ead184f0893a61f91492fb9f5</Id><ReqdMsgNmId>camt.052.001.02</ReqdMsgNmId><Acct><Id><IBAN>EE_TEST_IBAN</IBAN></Id></Acct><AcctOwnr><Pty/></AcctOwnr><RptgPrd><FrToDt><FrDt>2020-01-01</FrDt><ToDt>2020-01-01</ToDt></FrToDt><FrToTm><FrTm>00:00:00+02:00</FrTm><ToTm>23:59:59.999+02:00</ToTm></FrToTm><Tp>ALLL</Tp></RptgPrd></RptgReq></AcctRptgReq></Document>",
        requestXml);
  }

  @Disabled // TODO re-enable with camt052
  @Test
  @DisplayName("unmarshals statement response class, builds statement from it")
  public void unmarshalStatementResponseClass() {
    var responseXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.02\"> <BkToCstmrStmt> <GrpHdr> <MsgId>202506051114025350001</MsgId> <CreDtTm>2025-06-05T09:04:24</CreDtTm> </GrpHdr> <Stmt> <Id>111402535-EUR-1</Id> <ElctrncSeqNb>111402535</ElctrncSeqNb> <CreDtTm>2025-06-05T09:04:24</CreDtTm> <FrToDt> <FrDtTm>2025-06-05T00:00:00</FrDtTm> <ToDtTm>2025-06-05T23:59:59</ToDtTm> </FrToDt> <Acct> <Id> <IBAN>EE062200221055091966</IBAN> </Id> <Ccy>EUR</Ccy> <Ownr> <Nm>Pööripäeva Päikesekell OÜ</Nm> <PstlAdr> <Ctry>EE</Ctry> <AdrLine>Universumimaagia tee 15, Astrofantaasia, 56789</AdrLine> </PstlAdr> <Id> <OrgId> <Othr> <Id>10006901</Id> </Othr> </OrgId> </Id> </Ownr> <Svcr> <FinInstnId> <BIC>HABAEE2X</BIC> <Nm>SWEDBANK AS</Nm> <PstlAdr> <Ctry>EE</Ctry> <AdrLine>LIIVALAIA 8, 15040, TALLINN</AdrLine> </PstlAdr> <Othr> <Id>10060701</Id> <SchmeNm> <Cd>COID</Cd> </SchmeNm> </Othr> </FinInstnId> </Svcr> </Acct> <Bal> <Tp> <CdOrPrtry> <Cd>OPBD</Cd> </CdOrPrtry> </Tp> <CdtLine> <Incl>false</Incl> </CdtLine> <Amt Ccy=\"EUR\">3332.17</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Dt> <Dt>2025-06-05</Dt> </Dt> </Bal> <Bal> <Tp> <CdOrPrtry> <Cd>CLBD</Cd> </CdOrPrtry> </Tp> <Amt Ccy=\"EUR\">766.10</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Dt> <Dt>2025-06-05</Dt> </Dt> </Bal> <TxsSummry> <TtlCdtNtries> <NbOfNtries>4</NbOfNtries> <Sum>2566.07</Sum> </TtlCdtNtries> <TtlDbtNtries> <NbOfNtries>0</NbOfNtries> <Sum>0</Sum> </TtlDbtNtries> </TxsSummry> <Ntry> <NtryRef>2025060525827070-1</NtryRef> <Amt Ccy=\"EUR\">772.88</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Sts>BOOK</Sts> <BookgDt> <Dt>2025-06-05</Dt> </BookgDt> <ValDt> <Dt>2025-06-05</Dt> </ValDt> <AcctSvcrRef>2025060525827070-1</AcctSvcrRef> <BkTxCd> <Domn> <Cd>PMNT</Cd> <Fmly> <Cd>RCDT</Cd> <SubFmlyCd>BOOK</SubFmlyCd> </Fmly> </Domn> <Prtry> <Cd>MK</Cd> <Issr>SWEDBANK AS</Issr> </Prtry> </BkTxCd> <NtryDtls> <TxDtls> <Refs> <AcctSvcrRef>2025060525827070-1</AcctSvcrRef> <InstrId>179</InstrId> </Refs> <AmtDtls> <InstdAmt> <Amt Ccy=\"EUR\">772.88</Amt> </InstdAmt> <TxAmt> <Amt Ccy=\"EUR\">772.88</Amt> </TxAmt> </AmtDtls> <RltdPties> <Dbtr> <Nm>Toomas Raudsepp</Nm> <Id> <PrvtId> <Othr> <Id>38009293505</Id> <SchmeNm> <Cd>NIDN</Cd> </SchmeNm> </Othr> </PrvtId> </Id> </Dbtr> <DbtrAcct> <Id> <IBAN>EE042200000000100031</IBAN> </Id> </DbtrAcct> </RltdPties> <RltdAgts> <DbtrAgt> <FinInstnId> <BIC>HABAEE2X</BIC> </FinInstnId> </DbtrAgt> </RltdAgts> <RmtInf> <Ustrd>Subscription renewal for a productivity app or software</Ustrd> </RmtInf> </TxDtls> </NtryDtls> </Ntry> <Ntry> <NtryRef>2025060591569146-2</NtryRef> <Amt Ccy=\"EUR\">612.37</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Sts>BOOK</Sts> <BookgDt> <Dt>2025-06-05</Dt> </BookgDt> <ValDt> <Dt>2025-06-05</Dt> </ValDt> <AcctSvcrRef>2025060591569146-2</AcctSvcrRef> <BkTxCd> <Domn> <Cd>PMNT</Cd> <Fmly> <Cd>RCDT</Cd> <SubFmlyCd>BOOK</SubFmlyCd> </Fmly> </Domn> <Prtry> <Cd>MK</Cd> <Issr>SWEDBANK AS</Issr> </Prtry> </BkTxCd> <NtryDtls> <TxDtls> <Refs> <AcctSvcrRef>2025060591569146-2</AcctSvcrRef> <InstrId>180</InstrId> </Refs> <AmtDtls> <InstdAmt> <Amt Ccy=\"EUR\">612.37</Amt> </InstdAmt> <TxAmt> <Amt Ccy=\"EUR\">612.37</Amt> </TxAmt> </AmtDtls> <RltdPties> <Dbtr> <Nm>Kristjan Värk</Nm> <Id> <PrvtId> <Othr> <Id>39609307495</Id> <SchmeNm> <Cd>NIDN</Cd> </SchmeNm> </Othr> </PrvtId> </Id> </Dbtr> <DbtrAcct> <Id> <IBAN>EE082200000000100056</IBAN> </Id> </DbtrAcct> </RltdPties> <RltdAgts> <DbtrAgt> <FinInstnId> <BIC>HABAEE2X</BIC> </FinInstnId> </DbtrAgt> </RltdAgts> <RmtInf> <Ustrd>Purchase of airline tickets</Ustrd> </RmtInf> </TxDtls> </NtryDtls> </Ntry> <Ntry> <NtryRef>2025060538364998-3</NtryRef> <Amt Ccy=\"EUR\">454.76</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Sts>BOOK</Sts> <BookgDt> <Dt>2025-06-05</Dt> </BookgDt> <ValDt> <Dt>2025-06-05</Dt> </ValDt> <AcctSvcrRef>2025060538364998-3</AcctSvcrRef> <BkTxCd> <Domn> <Cd>PMNT</Cd> <Fmly> <Cd>RCDT</Cd> <SubFmlyCd>BOOK</SubFmlyCd> </Fmly> </Domn> <Prtry> <Cd>MK</Cd> <Issr>SWEDBANK AS</Issr> </Prtry> </BkTxCd> <NtryDtls> <TxDtls> <Refs> <AcctSvcrRef>2025060538364998-3</AcctSvcrRef> <InstrId>181</InstrId> </Refs> <AmtDtls> <InstdAmt> <Amt Ccy=\"EUR\">454.76</Amt> </InstdAmt> <TxAmt> <Amt Ccy=\"EUR\">454.76</Amt> </TxAmt> </AmtDtls> <RltdPties> <Dbtr> <Nm>Jüri Tamm</Nm> <Id> <PrvtId> <Othr> <Id>39910273027</Id> <SchmeNm> <Cd>NIDN</Cd> </SchmeNm> </Othr> </PrvtId> </Id> </Dbtr> <DbtrAcct> <Id> <IBAN>EE532200000000010006</IBAN> </Id> </DbtrAcct> </RltdPties> <RltdAgts> <DbtrAgt> <FinInstnId> <BIC>HABAEE2X</BIC> </FinInstnId> </DbtrAgt> </RltdAgts> <RmtInf> <Ustrd>Donation to a charity organization</Ustrd> </RmtInf> </TxDtls> </NtryDtls> </Ntry> <Ntry> <NtryRef>2025060515070077-4</NtryRef> <Amt Ccy=\"EUR\">726.06</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Sts>BOOK</Sts> <BookgDt> <Dt>2025-06-05</Dt> </BookgDt> <ValDt> <Dt>2025-06-05</Dt> </ValDt> <AcctSvcrRef>2025060515070077-4</AcctSvcrRef> <BkTxCd> <Domn> <Cd>PMNT</Cd> <Fmly> <Cd>RCDT</Cd> <SubFmlyCd>BOOK</SubFmlyCd> </Fmly> </Domn> <Prtry> <Cd>MK</Cd> <Issr>SWEDBANK AS</Issr> </Prtry> </BkTxCd> <NtryDtls> <TxDtls> <Refs> <AcctSvcrRef>2025060515070077-4</AcctSvcrRef> <InstrId>182</InstrId> </Refs> <AmtDtls> <InstdAmt> <Amt Ccy=\"EUR\">726.06</Amt> </InstdAmt> <TxAmt> <Amt Ccy=\"EUR\">726.06</Amt> </TxAmt> </AmtDtls> <RltdPties> <Dbtr> <Nm>Rasmus Lind</Nm> </Dbtr> <DbtrAcct> <Id> <IBAN>EE522200000000100040</IBAN> </Id> </DbtrAcct> </RltdPties> <RltdAgts> <DbtrAgt> <FinInstnId> <BIC>HABAEE2X</BIC> </FinInstnId> </DbtrAgt> </RltdAgts> <RmtInf> <Ustrd>Shopping for books or e-books</Ustrd> </RmtInf> </TxDtls> </NtryDtls> </Ntry> </Stmt> </BkToCstmrStmt> </Document>";

    JAXBElement<Document> response =
        swedbankGatewayMarshaller.unMarshal(responseXml, JAXBElement.class);

    var statement = response.getValue().getBkToCstmrAcctRpt().getRpt().getFirst();
    assertEquals("EE062200221055091966", statement.getAcct().getId().getIBAN());
    assertEquals("Pööripäeva Päikesekell OÜ", statement.getAcct().getOwnr().getNm());

    var entries = statement.getNtry();

    assertEquals(4, entries.size());

    var parsed = BankStatement.from(response.getValue().getBkToCstmrAcctRpt().getRpt().getFirst());

    // TODO more asserts
    assertEquals("EE062200221055091966", parsed.getBankStatementAccountType().iban());
    assertEquals(2, parsed.getBalances().size());

    var firstBalance = parsed.getBalances().getFirst();
    assertEquals(OPEN, firstBalance.type());
    assertEquals(LocalDate.of(2025, 6, 5), firstBalance.time());
    assertEquals(0, new BigDecimal("3332.17").compareTo(firstBalance.balance()));

    var secondBalance = parsed.getBalances().get(1);
    assertEquals(CLOSE, secondBalance.type());
    assertEquals(LocalDate.of(2025, 6, 5), secondBalance.time());
    assertEquals(0, new BigDecimal("766.10").compareTo(secondBalance.balance()));

    assertEquals(4, parsed.getEntries().size());

    var firstEntry = parsed.getEntries().get(0);
    assertEquals("Toomas Raudsepp", firstEntry.details().getName());
    assertEquals("EE042200000000100031", firstEntry.details().getIban());
    assertEquals(Optional.of("38009293505"), firstEntry.details().getPersonalCode());
    assertEquals(0, new BigDecimal("772.88").compareTo(firstEntry.amount()));

    var secondEntry = parsed.getEntries().get(1);
    assertEquals("Kristjan Värk", secondEntry.details().getName());
    assertEquals("EE082200000000100056", secondEntry.details().getIban());
    assertEquals(Optional.of("39609307495"), secondEntry.details().getPersonalCode());
    assertEquals(0, new BigDecimal("612.37").compareTo(secondEntry.amount()));

    var thirdEntry = parsed.getEntries().get(2);
    assertEquals("Jüri Tamm", thirdEntry.details().getName());
    assertEquals("EE532200000000010006", thirdEntry.details().getIban());
    assertEquals(Optional.of("39910273027"), thirdEntry.details().getPersonalCode());
    assertEquals(0, new BigDecimal("454.76").compareTo(thirdEntry.amount()));

    var fourthEntry = parsed.getEntries().get(3);
    assertEquals("Rasmus Lind", fourthEntry.details().getName());
    assertEquals("EE522200000000100040", fourthEntry.details().getIban());
    assertEquals(Optional.empty(), fourthEntry.details().getPersonalCode());
    assertEquals(0, new BigDecimal("726.06").compareTo(fourthEntry.amount()));
  }
}
