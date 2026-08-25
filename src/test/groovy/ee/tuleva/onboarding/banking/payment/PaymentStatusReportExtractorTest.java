package ee.tuleva.onboarding.banking.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentStatusReportExtractorTest {

  private final PaymentStatusReportExtractor extractor = new PaymentStatusReportExtractor();

  @Test
  void readsThePerPaymentVerdictAndItsReason() {
    var report =
        extractor.extract(
            report(
                """
        <OrgnlPmtInfAndSts>
          <TxInfAndSts>
            <OrgnlEndToEndId>abc123</OrgnlEndToEndId>
            <TxSts>RJCT</TxSts>
            <StsRsnInf><Rsn><Cd>AC01</Cd></Rsn></StsRsnInf>
          </TxInfAndSts>
        </OrgnlPmtInfAndSts>
        """));

    assertThat(report.transactionStatuses()).hasSize(1);
    var transaction = report.transactionStatuses().getFirst();
    assertThat(transaction.endToEndId()).isEqualTo("abc123");
    assertThat(transaction.status()).isEqualTo(PaymentStatus.RJCT);
    assertThat(transaction.status().isRejection()).isTrue();
    assertThat(transaction.reasonCode()).isEqualTo("AC01");
  }

  @Test
  void readsEveryPaymentInTheReport() {
    var report =
        extractor.extract(
            report(
                """
        <OrgnlPmtInfAndSts>
          <TxInfAndSts>
            <OrgnlEndToEndId>one</OrgnlEndToEndId><TxSts>ACSC</TxSts>
          </TxInfAndSts>
          <TxInfAndSts>
            <OrgnlEndToEndId>two</OrgnlEndToEndId><TxSts>RJCT</TxSts>
          </TxInfAndSts>
        </OrgnlPmtInfAndSts>
        """));

    assertThat(report.transactionStatuses()).hasSize(2);
    assertThat(extractor.rejections(report))
        .singleElement()
        .satisfies(transaction -> assertThat(transaction.endToEndId()).isEqualTo("two"));
  }

  @Test
  void aFileLevelOnlyReportIsRecognisedRatherThanMisreadAsAnAcceptance() {
    // Whether SEB reports per payment or only per file has never been confirmed, so the shape has
    // to be distinguishable rather than assumed.
    var report = extractor.extract(report("<GrpSts>ACTC</GrpSts>"));

    assertThat(report.isFileLevelOnly()).isTrue();
    assertThat(report.groupStatus()).isEqualTo("ACTC");
  }

  @Test
  void anUnknownStatusCodeIsNotMistakenForARejection() {
    var report =
        extractor.extract(
            report(
                """
        <OrgnlPmtInfAndSts>
          <TxInfAndSts>
            <OrgnlEndToEndId>abc123</OrgnlEndToEndId><TxSts>WHAT</TxSts>
          </TxInfAndSts>
        </OrgnlPmtInfAndSts>
        """));

    assertThat(report.transactionStatuses().getFirst().status()).isEqualTo(PaymentStatus.UNKNOWN);
    assertThat(extractor.rejections(report)).isEmpty();
  }

  @Test
  void aTransactionWithoutAnOriginalIdIsSkippedRatherThanGuessedAt() {
    var report =
        extractor.extract(
            report(
                """
        <OrgnlPmtInfAndSts>
          <TxInfAndSts><TxSts>RJCT</TxSts></TxInfAndSts>
        </OrgnlPmtInfAndSts>
        """));

    assertThat(report.transactionStatuses()).isEmpty();
  }

  private static String report(String body) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.002.001.10">
          <CstmrPmtStsRpt>
            %s
          </CstmrPmtStsRpt>
        </Document>
        """
        .formatted(body);
  }
}
