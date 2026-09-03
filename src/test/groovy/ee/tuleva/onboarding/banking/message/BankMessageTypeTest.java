package ee.tuleva.onboarding.banking.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BankMessageTypeTest {

  @Test
  void fromXmlType_resolvesKnownMessageType() {
    assertThat(BankMessageType.fromXmlType("camt.053.001.02"))
        .isEqualTo(BankMessageType.HISTORIC_STATEMENT);
  }

  @Test
  void fromXmlType_throwsForUnknownMessageType() {
    assertThatThrownBy(() -> BankMessageType.fromXmlType("unknown.type"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
