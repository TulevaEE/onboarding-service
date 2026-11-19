package ee.tuleva.onboarding.banking.message;

import static ee.tuleva.onboarding.banking.message.BankMessageType.HISTORIC_STATEMENT;
import static ee.tuleva.onboarding.banking.message.BankMessageType.INTRA_DAY_REPORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

class BankMessageTypeTest {

  @Test
  void ofMessage_isAHistoricStatementForACamt053Document() {
    assertThat(
            BankMessageType.ofMessage(
                "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.02\"/>"))
        .isEqualTo(HISTORIC_STATEMENT);
  }

  @Test
  void ofMessage_isAnIntraDayReportForACamt052Document() {
    assertThat(
            BankMessageType.ofMessage(
                "<?xml version=\"1.0\"?><Document"
                    + " xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"/>"))
        .isEqualTo(INTRA_DAY_REPORT);
  }

  @Test
  void ofMessage_followsTheRootElementWhenAnotherMessageTypeIsNamedInside() {
    assertThat(
            BankMessageType.ofMessage(
                "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.052.001.02\"><Ntry"
                    + " xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.02\"/></Document>"))
        .isEqualTo(INTRA_DAY_REPORT);
  }

  @Test
  void ofMessage_throwsForAnUnknownMessageType() {
    assertThatThrownBy(
            () ->
                BankMessageType.ofMessage(
                    "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.002.001.03\"/>"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ofMessage_throwsForADocumentWithoutANamespace() {
    assertThatThrownBy(() -> BankMessageType.ofMessage("<Document/>"))
        .isInstanceOf(NoSuchElementException.class);
  }
}
