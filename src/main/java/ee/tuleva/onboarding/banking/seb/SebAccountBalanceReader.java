package ee.tuleva.onboarding.banking.seb;

import ee.tuleva.onboarding.banking.BankAccount;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * Reads an account's balance straight from the bank.
 *
 * <p>{@code SebGatewayClient.getBalances} has been implemented and tested since it was written and
 * has never had a production caller, so what the bank actually returns here is unproven. That is
 * why this returns an {@link Optional} and every failure is a shrug rather than an exception: the
 * balance feeds an informational line, and an informational line is not worth failing a job over.
 * Promote it to something that can block only after it has been watched against reality.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SebAccountBalanceReader {

  /** Interim available first — that is the one that reflects what is actually spendable. */
  private static final List<String> PREFERRED_TYPES =
      List.of("ITAV", "AVL", "CLAV", "ITBD", "CLBD");

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

    var balances = document.getElementsByTagNameNS("*", "Bal");
    for (var preferred : PREFERRED_TYPES) {
      for (int i = 0; i < balances.getLength(); i++) {
        var balance = (Element) balances.item(i);
        if (preferred.equals(text(balance, "Cd"))) {
          var amount = text(balance, "Amt");
          if (amount != null) {
            return Optional.of(signed(new BigDecimal(amount), text(balance, "CdtDbtInd")));
          }
        }
      }
    }
    log.warn("No recognised balance type in the bank's response");
    return Optional.empty();
  }

  private static BigDecimal signed(BigDecimal amount, String creditDebitIndicator) {
    return "DBIT".equals(creditDebitIndicator) ? amount.negate() : amount;
  }

  private static String text(Element parent, String localName) {
    var found = parent.getElementsByTagNameNS("*", localName);
    return found.getLength() == 0 ? null : found.item(0).getTextContent().trim();
  }
}
