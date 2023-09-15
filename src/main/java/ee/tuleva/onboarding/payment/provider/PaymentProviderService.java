package ee.tuleva.onboarding.payment.provider;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentLinkGenerator;
import java.net.URL;
import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentProviderService implements PaymentLinkGenerator {

  private final Clock clock;

  private final PaymentInternalReferenceService paymentInternalReferenceService;

  private final PaymentProviderConfiguration paymentProviderConfiguration;

  private final LocaleService localeService;

  @Value("${payment-provider.url}")
  private String paymentProviderUrl;

  @Value("${api.url}")
  private String apiUrl;

  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    Map<String, Object> payload = new HashMap<>();
    PaymentProviderBank bankConfiguration =
        paymentProviderConfiguration.getPaymentProviderBank(paymentData.getBank());

    payload.put("currency", paymentData.getCurrency());
    payload.put("amount", paymentData.getAmount());
    payload.put("access_key", bankConfiguration.getAccessKey());
    payload.put(
        "merchant_reference",
        paymentInternalReferenceService.getPaymentReference(person, paymentData));
    payload.put("merchant_return_url", apiUrl + "/payments/success");
    payload.put("merchant_notification_url", apiUrl + "/payments/notification");

    payload.put("payment_information_unstructured", getPaymentInformationUnstructured(paymentData));

    payload.put("preselected_locale", getLanguage());
    payload.put("exp", clock.instant().getEpochSecond() + 600);
    payload.put("checkout_first_name", person.getFirstName());
    payload.put("checkout_last_name", person.getLastName());
    payload.put("preselected_aspsp", bankConfiguration.getBic());

    JWSObject jwsObject = getSignedJws(payload, bankConfiguration);
    URL url = getUrl(jwsObject);

    return new PaymentLink(url.toString());
  }

  private static String getPaymentInformationUnstructured(PaymentData paymentData) {
    return (paymentData.getType() == PaymentData.PaymentType.MEMBER_FEE)
        ? String.format("IK:%s", paymentData.getRecipientPersonalCode())
        : String.format("30101119828, IK:%s, EE3600001707", paymentData.getRecipientPersonalCode());
  }

  private String getLanguage() {
    Locale locale = localeService.getCurrentLocale();
    return Locale.ENGLISH.getLanguage().equals(locale.getLanguage())
        ? "en_US"
        : locale.getLanguage();
  }

  @SneakyThrows
  private URL getUrl(JWSObject jwsObject) {
    return new URIBuilder(paymentProviderUrl)
        .addParameter("payment_token", jwsObject.serialize())
        .build()
        .toURL();
  }

  @SneakyThrows
  private JWSObject getSignedJws(
      Map<String, Object> payload, PaymentProviderBank bankConfiguration) {
    JWSObject jwsObject = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(payload));
    jwsObject.sign(new MACSigner(bankConfiguration.secretKey.getBytes()));
    return jwsObject;
  }
}
