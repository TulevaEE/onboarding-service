package ee.tuleva.onboarding.swedbank.processor;

import java.util.Arrays;
import lombok.Getter;

public enum SwedbankMessageType {
  INTRA_DAY_REPORT("camt.052.001.02"),
  HISTORIC_STATEMENT("camt.053.001.02"),
  PAYMENT_ORDER_CONFIRMATION("pain.002.001.10");

  @Getter private final String xmlType;

  SwedbankMessageType(String xmlType) {
    this.xmlType = xmlType;
  }

  public static SwedbankMessageType fromXmlType(String xmlType) {
    return Arrays.stream(SwedbankMessageType.values())
        .filter(type -> type.xmlType.equals(xmlType))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Cannot find XML message type"));
  }
}
