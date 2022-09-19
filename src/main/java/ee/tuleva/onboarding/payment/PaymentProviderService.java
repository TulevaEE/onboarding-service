package ee.tuleva.onboarding.payment;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProviderService {

  private final Clock clock;

  private final EpisService episService;

  private final PaymentInternalReferenceService paymentInternalReferenceService;

//  private final Map<String, PaymentProviderBankConfiguration> paymentProviderBankConfigurations;

  private final PaymentProviderConfiguration paymentProviderConfiguration;

  @Value("${payment-provider.url}")
  private String paymentProviderUrl;

  @Value("${api.url}")
  private String apiUrl;

  public String getPaymentUrl(PaymentData paymentData) {

    ContactDetails contactDetails = episService.getContactDetails(paymentData.getPerson());

    PaymentProviderBankConfiguration bankConfiguration =
        paymentProviderConfiguration.getByBank(paymentData.getBank());

    Map<String, Object> payload = new HashMap<>();
//    PaymentProviderBankConfiguration bankConfiguration =
//        paymentProviderBankConfigurations.get(paymentData.getBank().getBeanName());

    payload.put("currency", paymentData.getCurrency());
    payload.put("amount", paymentData.getAmount());
    payload.put("access_key", bankConfiguration.getAccessKey());
    payload.put(
        "merchant_reference",
        paymentInternalReferenceService.getPaymentReference(paymentData.getPerson()));
    payload.put("merchant_return_url", apiUrl + "/payments/success");
    payload.put("merchant_notification_url", apiUrl + "/payments/notification");
    payload.put("payment_information_unstructured", "30101119828");
    payload.put("payment_information_structured", contactDetails.getPensionAccountNumber());
    payload.put("preselected_locale", "et");
    payload.put("exp", clock.instant().getEpochSecond() + 600);
    payload.put("checkout_first_name", paymentData.getPerson().getFirstName());
    payload.put("checkout_last_name", paymentData.getPerson().getLastName());
    payload.put("preselected_aspsp", bankConfiguration.getBic());

    JWSObject jwsObject = getSignedJws(payload, bankConfiguration);
    URL url = getUrl(jwsObject);
    return url.toString();
  }

  private URL getUrl(JWSObject jwsObject) {
    try {
      return new URIBuilder(paymentProviderUrl)
          .addParameter("payment_token", jwsObject.serialize())
          .build()
          .toURL();
    } catch (URISyntaxException | MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  private JWSObject getSignedJws(
      Map<String, Object> payload, PaymentProviderBankConfiguration bankConfiguration) {
    JWSObject jwsObject = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(payload));
    try {
      jwsObject.sign(new MACSigner(bankConfiguration.secretKey.getBytes()));
    } catch (JOSEException e) {
      throw new RuntimeException(e);
    }
    return jwsObject;
  }
}
