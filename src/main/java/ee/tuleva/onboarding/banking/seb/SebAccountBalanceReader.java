package ee.tuleva.onboarding.banking.seb;

import ee.tuleva.onboarding.banking.BankAccount;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

@Slf4j
@Component
@RequiredArgsConstructor
public class SebAccountBalanceReader {
  private static final String DEBIT = "DBIT";

  private final SebGatewayClient sebGatewayClient;

  public Optional<BigDecimal> available(BankAccount account) {
    try {
      var xml = sebGatewayClient.getBalances(account.iban(), account.gatewayClientId());
      return parse(xml);
    } catch (Exception e) {
      log.warn("Could not read the account balance: account={}", account, e);
      return Optional.empty();
    }
  }

  Optional<BigDecimal> parse(String xml) throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));

    var reportedBalances = document.getElementsByTagNameNS("*", "Bal");
    for (var wantedType : BalanceType.mostSpendableFirst()) {
      for (int i = 0; i < reportedBalances.getLength(); i++) {
        var reportedBalance = (Element) reportedBalances.item(i);
        if (wantedType.isoCode.equals(text(reportedBalance, "Cd"))) {
          var reportedAmount = text(reportedBalance, "Amt");
          if (reportedAmount != null) {
            return Optional.of(
                signed(new BigDecimal(reportedAmount), text(reportedBalance, "CdtDbtInd")));
          }
        }
      }
    }
    log.warn("No recognised balance type in the bank's response");
    return Optional.empty();
  }

  private static BigDecimal signed(BigDecimal amount, @Nullable String creditDebitIndicator) {
    return DEBIT.equals(creditDebitIndicator) ? amount.negate() : amount;
  }

  private static @Nullable String text(Element parent, String localName) {
    var found = parent.getElementsByTagNameNS("*", localName);
    return found.getLength() == 0 ? null : found.item(0).getTextContent().trim();
  }

  private enum BalanceType {
    INTERIM_AVAILABLE("ITAV"),
    AVAILABLE("AVL"),
    CLOSING_AVAILABLE("CLAV"),
    INTERIM_BOOKED("ITBD"),
    CLOSING_BOOKED("CLBD");

    private final String isoCode;

    BalanceType(String isoCode) {
      this.isoCode = isoCode;
    }

    private static BalanceType[] mostSpendableFirst() {
      return values();
    }
  }
}
