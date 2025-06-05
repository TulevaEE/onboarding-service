package ee.tuleva.onboarding.swedbank.http;

import static org.junit.jupiter.api.Assertions.*;

import ee.swedbank.gateway.iso.response.Document;
import jakarta.xml.bind.JAXBElement;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SwedbankGatewayMarshallerTest {

  @Autowired SwedbankGatewayMarshaller swedbankGatewayMarshaller;

  @Autowired SwedbankGatewayClient swedbankGatewayClient;

  @Test
  @DisplayName("marshals request class")
  public void marshalRequestClass() {
    var request =
        swedbankGatewayClient.getAccountStatementRequestEntity(
            "EE_TEST_IBAN", UUID.fromString("cdb18c2e-ad18-4f08-93a6-1f91492fb9f5"));

    var requestXml = swedbankGatewayMarshaller.marshalToString(request);

    assertEquals(
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ns0:Document xmlns:ns0=\"urn:iso:std:iso:20022:tech:xsd:camt.060.001.03\"><ns0:AcctRptgReq><ns0:GrpHdr><ns0:MsgId>cdb18c2ead184f0893a61f91492fb9f5</ns0:MsgId><ns0:CreDtTm>2025-06-05T12:34:11.100+03:00</ns0:CreDtTm></ns0:GrpHdr><ns0:RptgReq><ns0:Id>cdb18c2ead184f0893a61f91492fb9f5</ns0:Id><ns0:ReqdMsgNmId>camt.053.001.02</ns0:ReqdMsgNmId><ns0:Acct><ns0:Id><ns0:IBAN>EE_TEST_IBAN</ns0:IBAN></ns0:Id></ns0:Acct><ns0:AcctOwnr><ns0:Pty><ns0:Nm>Tuleva</ns0:Nm></ns0:Pty></ns0:AcctOwnr><ns0:RptgPrd><ns0:FrToDt><ns0:FrDt>2025-06-05</ns0:FrDt><ns0:ToDt>2025-06-05</ns0:ToDt></ns0:FrToDt><ns0:FrToTm><ns0:FrTm>03:00:00+03:00</ns0:FrTm><ns0:ToTm>03:00:00+03:00</ns0:ToTm></ns0:FrToTm><ns0:Tp>ALLL</ns0:Tp></ns0:RptgPrd></ns0:RptgReq></ns0:AcctRptgReq></ns0:Document>",
        requestXml);
  }

  @Test
  @DisplayName("unmarshals statement response class")
  public void unmarshalStatementResponseClass() {
    var responseXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?> <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.02\"> <BkToCstmrStmt> <GrpHdr> <MsgId>202506051114025350001</MsgId> <CreDtTm>2025-06-05T09:04:24</CreDtTm> </GrpHdr> <Stmt> <Id>111402535-EUR-1</Id> <ElctrncSeqNb>111402535</ElctrncSeqNb> <CreDtTm>2025-06-05T09:04:24</CreDtTm> <FrToDt> <FrDtTm>2025-06-05T00:00:00</FrDtTm> <ToDtTm>2025-06-05T23:59:59</ToDtTm> </FrToDt> <Acct> <Id> <IBAN>EE062200221055091966</IBAN> </Id> <Ccy>EUR</Ccy> <Ownr> <Nm>Pööripäeva Päikesekell OÜ</Nm> <PstlAdr> <Ctry>EE</Ctry> <AdrLine>Universumimaagia tee 15, Astrofantaasia, 56789</AdrLine> </PstlAdr> <Id> <OrgId> <Othr> <Id>10006901</Id> </Othr> </OrgId> </Id> </Ownr> <Svcr> <FinInstnId> <BIC>HABAEE2X</BIC> <Nm>SWEDBANK AS</Nm> <PstlAdr> <Ctry>EE</Ctry> <AdrLine>LIIVALAIA 8, 15040, TALLINN</AdrLine> </PstlAdr> <Othr> <Id>10060701</Id> <SchmeNm> <Cd>COID</Cd> </SchmeNm> </Othr> </FinInstnId> </Svcr> </Acct> <Bal> <Tp> <CdOrPrtry> <Cd>OPBD</Cd> </CdOrPrtry> </Tp> <CdtLine> <Incl>false</Incl> </CdtLine> <Amt Ccy=\"EUR\">3332.17</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Dt> <Dt>2025-06-05</Dt> </Dt> </Bal> <Bal> <Tp> <CdOrPrtry> <Cd>CLBD</Cd> </CdOrPrtry> </Tp> <Amt Ccy=\"EUR\">766.10</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Dt> <Dt>2025-06-05</Dt> </Dt> </Bal> <TxsSummry> <TtlCdtNtries> <NbOfNtries>4</NbOfNtries> <Sum>2566.07</Sum> </TtlCdtNtries> <TtlDbtNtries> <NbOfNtries>0</NbOfNtries> <Sum>0</Sum> </TtlDbtNtries> </TxsSummry> <Ntry> <NtryRef>2025060525827070-1</NtryRef> <Amt Ccy=\"EUR\">772.88</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Sts>BOOK</Sts> <BookgDt> <Dt>2025-06-05</Dt> </BookgDt> <ValDt> <Dt>2025-06-05</Dt> </ValDt> <AcctSvcrRef>2025060525827070-1</AcctSvcrRef> <BkTxCd> <Domn> <Cd>PMNT</Cd> <Fmly> <Cd>RCDT</Cd> <SubFmlyCd>BOOK</SubFmlyCd> </Fmly> </Domn> <Prtry> <Cd>MK</Cd> <Issr>SWEDBANK AS</Issr> </Prtry> </BkTxCd> <NtryDtls> <TxDtls> <Refs> <AcctSvcrRef>2025060525827070-1</AcctSvcrRef> <InstrId>179</InstrId> </Refs> <AmtDtls> <InstdAmt> <Amt Ccy=\"EUR\">772.88</Amt> </InstdAmt> <TxAmt> <Amt Ccy=\"EUR\">772.88</Amt> </TxAmt> </AmtDtls> <RltdPties> <Dbtr> <Nm>Toomas Raudsepp</Nm> <Id> <PrvtId> <Othr> <Id>38009293505</Id> <SchmeNm> <Cd>NIDN</Cd> </SchmeNm> </Othr> </PrvtId> </Id> </Dbtr> <DbtrAcct> <Id> <IBAN>EE042200000000100031</IBAN> </Id> </DbtrAcct> </RltdPties> <RltdAgts> <DbtrAgt> <FinInstnId> <BIC>HABAEE2X</BIC> </FinInstnId> </DbtrAgt> </RltdAgts> <RmtInf> <Ustrd>Subscription renewal for a productivity app or software</Ustrd> </RmtInf> </TxDtls> </NtryDtls> </Ntry> <Ntry> <NtryRef>2025060591569146-2</NtryRef> <Amt Ccy=\"EUR\">612.37</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Sts>BOOK</Sts> <BookgDt> <Dt>2025-06-05</Dt> </BookgDt> <ValDt> <Dt>2025-06-05</Dt> </ValDt> <AcctSvcrRef>2025060591569146-2</AcctSvcrRef> <BkTxCd> <Domn> <Cd>PMNT</Cd> <Fmly> <Cd>RCDT</Cd> <SubFmlyCd>BOOK</SubFmlyCd> </Fmly> </Domn> <Prtry> <Cd>MK</Cd> <Issr>SWEDBANK AS</Issr> </Prtry> </BkTxCd> <NtryDtls> <TxDtls> <Refs> <AcctSvcrRef>2025060591569146-2</AcctSvcrRef> <InstrId>180</InstrId> </Refs> <AmtDtls> <InstdAmt> <Amt Ccy=\"EUR\">612.37</Amt> </InstdAmt> <TxAmt> <Amt Ccy=\"EUR\">612.37</Amt> </TxAmt> </AmtDtls> <RltdPties> <Dbtr> <Nm>Kristjan Värk</Nm> <Id> <PrvtId> <Othr> <Id>39609307495</Id> <SchmeNm> <Cd>NIDN</Cd> </SchmeNm> </Othr> </PrvtId> </Id> </Dbtr> <DbtrAcct> <Id> <IBAN>EE082200000000100056</IBAN> </Id> </DbtrAcct> </RltdPties> <RltdAgts> <DbtrAgt> <FinInstnId> <BIC>HABAEE2X</BIC> </FinInstnId> </DbtrAgt> </RltdAgts> <RmtInf> <Ustrd>Purchase of airline tickets</Ustrd> </RmtInf> </TxDtls> </NtryDtls> </Ntry> <Ntry> <NtryRef>2025060538364998-3</NtryRef> <Amt Ccy=\"EUR\">454.76</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Sts>BOOK</Sts> <BookgDt> <Dt>2025-06-05</Dt> </BookgDt> <ValDt> <Dt>2025-06-05</Dt> </ValDt> <AcctSvcrRef>2025060538364998-3</AcctSvcrRef> <BkTxCd> <Domn> <Cd>PMNT</Cd> <Fmly> <Cd>RCDT</Cd> <SubFmlyCd>BOOK</SubFmlyCd> </Fmly> </Domn> <Prtry> <Cd>MK</Cd> <Issr>SWEDBANK AS</Issr> </Prtry> </BkTxCd> <NtryDtls> <TxDtls> <Refs> <AcctSvcrRef>2025060538364998-3</AcctSvcrRef> <InstrId>181</InstrId> </Refs> <AmtDtls> <InstdAmt> <Amt Ccy=\"EUR\">454.76</Amt> </InstdAmt> <TxAmt> <Amt Ccy=\"EUR\">454.76</Amt> </TxAmt> </AmtDtls> <RltdPties> <Dbtr> <Nm>Jüri Tamm</Nm> <Id> <PrvtId> <Othr> <Id>39910273027</Id> <SchmeNm> <Cd>NIDN</Cd> </SchmeNm> </Othr> </PrvtId> </Id> </Dbtr> <DbtrAcct> <Id> <IBAN>EE532200000000010006</IBAN> </Id> </DbtrAcct> </RltdPties> <RltdAgts> <DbtrAgt> <FinInstnId> <BIC>HABAEE2X</BIC> </FinInstnId> </DbtrAgt> </RltdAgts> <RmtInf> <Ustrd>Donation to a charity organization</Ustrd> </RmtInf> </TxDtls> </NtryDtls> </Ntry> <Ntry> <NtryRef>2025060515070077-4</NtryRef> <Amt Ccy=\"EUR\">726.06</Amt> <CdtDbtInd>CRDT</CdtDbtInd> <Sts>BOOK</Sts> <BookgDt> <Dt>2025-06-05</Dt> </BookgDt> <ValDt> <Dt>2025-06-05</Dt> </ValDt> <AcctSvcrRef>2025060515070077-4</AcctSvcrRef> <BkTxCd> <Domn> <Cd>PMNT</Cd> <Fmly> <Cd>RCDT</Cd> <SubFmlyCd>BOOK</SubFmlyCd> </Fmly> </Domn> <Prtry> <Cd>MK</Cd> <Issr>SWEDBANK AS</Issr> </Prtry> </BkTxCd> <NtryDtls> <TxDtls> <Refs> <AcctSvcrRef>2025060515070077-4</AcctSvcrRef> <InstrId>182</InstrId> </Refs> <AmtDtls> <InstdAmt> <Amt Ccy=\"EUR\">726.06</Amt> </InstdAmt> <TxAmt> <Amt Ccy=\"EUR\">726.06</Amt> </TxAmt> </AmtDtls> <RltdPties> <Dbtr> <Nm>Rasmus Lind</Nm> <Id> <PrvtId> <Othr> <Id>37603135585</Id> <SchmeNm> <Cd>NIDN</Cd> </SchmeNm> </Othr> </PrvtId> </Id> </Dbtr> <DbtrAcct> <Id> <IBAN>EE522200000000100040</IBAN> </Id> </DbtrAcct> </RltdPties> <RltdAgts> <DbtrAgt> <FinInstnId> <BIC>HABAEE2X</BIC> </FinInstnId> </DbtrAgt> </RltdAgts> <RmtInf> <Ustrd>Shopping for books or e-books</Ustrd> </RmtInf> </TxDtls> </NtryDtls> </Ntry> </Stmt> </BkToCstmrStmt> </Document>";

    JAXBElement<Document> response =
        swedbankGatewayMarshaller.unMarshal(responseXml, JAXBElement.class);

    var statement = response.getValue().getBkToCstmrStmt().getStmt().getFirst();
    assertEquals("EE062200221055091966", statement.getAcct().getId().getIBAN());
    assertEquals("Pööripäeva Päikesekell OÜ", statement.getAcct().getOwnr().getNm());

    var entries = statement.getNtry();

    assertEquals(4, entries.size());
  }
}
