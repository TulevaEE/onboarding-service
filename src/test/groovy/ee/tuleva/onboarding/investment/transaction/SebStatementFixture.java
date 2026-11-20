package ee.tuleva.onboarding.investment.transaction;

import java.time.LocalDate;

final class SebStatementFixture {

  private SebStatementFixture() {}

  static String currentDayReport(String iban, LocalDate day, String interimBooked) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.02">
          <BkToCstmrAcctRpt>
            <GrpHdr><MsgId>report</MsgId><CreDtTm>%1$sT09:00:00+02:00</CreDtTm></GrpHdr>
            <Rpt>
              <Id>report</Id>
              <CreDtTm>%1$sT09:00:00+02:00</CreDtTm>
              <FrToDt><FrDtTm>%1$sT00:00:00+02:00</FrDtTm><ToDtTm>%1$sT09:00:00+02:00</ToDtTm></FrToDt>
              <Acct>
                <Id><IBAN>%2$s</IBAN></Id>
                <Ownr><Nm>BGW TESTCLIENT1</Nm><Id><OrgId><Othr><Id>22255887</Id></Othr></OrgId></Id></Ownr>
              </Acct>
              <Bal>
                <Tp><CdOrPrtry><Cd>OPBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="EUR">%3$s</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt><Dt>%1$s</Dt></Dt>
              </Bal>
              <Bal>
                <Tp><CdOrPrtry><Cd>ITBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="EUR">%3$s</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt><Dt>%1$s</Dt></Dt>
              </Bal>
            </Rpt>
          </BkToCstmrAcctRpt>
        </Document>
        """
        .formatted(day, iban, interimBooked);
  }

  static String currentDayReportWithoutBookedBalance(String iban, LocalDate day) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.02">
          <BkToCstmrAcctRpt>
            <GrpHdr><MsgId>report</MsgId><CreDtTm>%1$sT09:00:00+02:00</CreDtTm></GrpHdr>
            <Rpt>
              <Id>report</Id>
              <CreDtTm>%1$sT09:00:00+02:00</CreDtTm>
              <FrToDt><FrDtTm>%1$sT00:00:00+02:00</FrDtTm><ToDtTm>%1$sT09:00:00+02:00</ToDtTm></FrToDt>
              <Acct>
                <Id><IBAN>%2$s</IBAN></Id>
                <Ownr><Nm>BGW TESTCLIENT1</Nm><Id><OrgId><Othr><Id>22255887</Id></Othr></OrgId></Id></Ownr>
              </Acct>
              <Bal>
                <Tp><CdOrPrtry><Cd>OPBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="EUR">0.00</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt><Dt>%1$s</Dt></Dt>
              </Bal>
            </Rpt>
          </BkToCstmrAcctRpt>
        </Document>
        """
        .formatted(day, iban);
  }

  static String endOfDayStatement(String iban, LocalDate day, String closingBooked) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.02">
          <BkToCstmrStmt>
            <GrpHdr><MsgId>statement</MsgId><CreDtTm>%1$sT23:30:00+02:00</CreDtTm></GrpHdr>
            <Stmt>
              <Id>statement</Id>
              <CreDtTm>%1$sT23:30:00+02:00</CreDtTm>
              <FrToDt><FrDtTm>%1$sT00:00:00+02:00</FrDtTm><ToDtTm>%1$sT23:59:59+02:00</ToDtTm></FrToDt>
              <Acct>
                <Id><IBAN>%2$s</IBAN></Id>
                <Ownr><Nm>BGW TESTCLIENT1</Nm><Id><OrgId><Othr><Id>22255887</Id></Othr></OrgId></Id></Ownr>
              </Acct>
              <Bal>
                <Tp><CdOrPrtry><Cd>OPBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="EUR">%3$s</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt><Dt>%1$s</Dt></Dt>
              </Bal>
              <Bal>
                <Tp><CdOrPrtry><Cd>CLBD</Cd></CdOrPrtry></Tp>
                <Amt Ccy="EUR">%3$s</Amt>
                <CdtDbtInd>CRDT</CdtDbtInd>
                <Dt><Dt>%1$s</Dt></Dt>
              </Bal>
            </Stmt>
          </BkToCstmrStmt>
        </Document>
        """
        .formatted(day, iban, closingBooked);
  }
}
