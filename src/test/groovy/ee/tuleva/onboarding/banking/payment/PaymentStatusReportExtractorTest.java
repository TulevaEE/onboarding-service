package ee.tuleva.onboarding.banking.payment;

import static ee.tuleva.onboarding.banking.payment.PaymentStatus.ACCEPTED_SETTLEMENT_COMPLETED;
import static ee.tuleva.onboarding.banking.payment.PaymentStatus.REJECTED;
import static ee.tuleva.onboarding.banking.payment.PaymentStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.banking.payment.PaymentStatusReport.TransactionStatus;
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

    assertThat(report.transactionStatuses())
        .containsExactly(new TransactionStatus("abc123", REJECTED, "AC01"));
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

    assertThat(report.transactionStatuses())
        .containsExactly(
            new TransactionStatus("one", ACCEPTED_SETTLEMENT_COMPLETED, null),
            new TransactionStatus("two", REJECTED, null));
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

    assertThat(report.transactionStatuses())
        .containsExactly(new TransactionStatus("abc123", UNKNOWN, null));
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

  // A status buried in the original-transaction block describes the original instruction, not what
  // the bank did with this one. Reading it as the transaction's own would mark the wrong payment
  // rejected, which is worse than reporting no status at all.
  @Test
  void aStatusNestedInAnOriginalTransactionBlockIsNotReadAsThisTransactionsOwn() {
    var report =
        extractor.extract(
            report(
                """
        <OrgnlPmtInfAndSts>
          <TxInfAndSts>
            <OrgnlEndToEndId>abc123</OrgnlEndToEndId>
            <OrgnlTxRef><TxSts>RJCT</TxSts></OrgnlTxRef>
          </TxInfAndSts>
        </OrgnlPmtInfAndSts>
        """));

    assertThat(report.transactionStatuses())
        .containsExactly(new TransactionStatus("abc123", UNKNOWN, null));
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
