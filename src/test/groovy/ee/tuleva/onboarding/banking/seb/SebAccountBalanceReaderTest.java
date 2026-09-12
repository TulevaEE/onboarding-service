package ee.tuleva.onboarding.banking.seb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SebAccountBalanceReaderTest {

  private final SebAccountBalanceReader reader =
      new SebAccountBalanceReader(mock(SebGatewayClient.class));

  @Test
  void readsTheAvailableBalance() throws Exception {
    assertThat(reader.parse(balances("ITAV", "1234.56", "CRDT")))
        .contains(new BigDecimal("1234.56"));
  }

  @Test
  void prefersWhatIsSpendableOverTheBookedBalance() throws Exception {
    var xml =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.02">
          <Bal><Tp><CdOrPrtry><Cd>CLBD</Cd></CdOrPrtry></Tp>
            <Amt Ccy="EUR">9999.99</Amt><CdtDbtInd>CRDT</CdtDbtInd></Bal>
          <Bal><Tp><CdOrPrtry><Cd>ITAV</Cd></CdOrPrtry></Tp>
            <Amt Ccy="EUR">10.00</Amt><CdtDbtInd>CRDT</CdtDbtInd></Bal>
        </Document>
        """;

    assertThat(reader.parse(xml)).contains(new BigDecimal("10.00"));
  }

  @Test
  void anOverdrawnAccountComesBackNegative() throws Exception {
    assertThat(reader.parse(balances("ITAV", "5.00", "DBIT"))).contains(new BigDecimal("-5.00"));
  }

  @Test
  void anUnrecognisedResponseIsAShrugRatherThanAnError() throws Exception {
    // What this endpoint actually returns has never been seen in production, so the reader has to
    // fail soft: the balance feeds an informational line, nothing is gated on it.
    assertThat(reader.parse("<Document><Whatever/></Document>")).isEmpty();
  }

  private static String balances(String type, String amount, String creditDebit) {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.02">
          <Bal>
            <Tp><CdOrPrtry><Cd>%s</Cd></CdOrPrtry></Tp>
            <Amt Ccy="EUR">%s</Amt>
            <CdtDbtInd>%s</CdtDbtInd>
          </Bal>
        </Document>
        """
        .formatted(type, amount, creditDebit);
  }
}
