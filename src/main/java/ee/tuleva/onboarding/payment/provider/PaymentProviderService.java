package ee.tuleva.onboarding.payment.provider;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentData.PaymentType;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentLinkGenerator;
import java.math.BigDecimal;
import java.net.URL;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

  @Value("${payment.member-fee}")
  private BigDecimal memberFee;

  @Value("${payment.member-fee-test-personal-code}")
  private String memberFeeTestPersonalCode;

  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    Map<String, Object> payload = new LinkedHashMap<>();
    PaymentProviderChannel paymentChannelConfiguration =
        paymentProviderConfiguration.getPaymentProviderChannel(paymentData.getPaymentChannel());

    payload.put("currency", paymentData.getCurrency());
    payload.put("amount", getPaymentAmount(paymentData));
    payload.put("access_key", paymentChannelConfiguration.getAccessKey());
    payload.put(
        "merchant_reference",
        paymentInternalReferenceService.getPaymentReference(person, paymentData));
    payload.put("merchant_return_url", getPaymentSuccessReturnUrl(paymentData.getType()));
    payload.put("merchant_notification_url", apiUrl + "/payments/notification");

    payload.put("payment_information_unstructured", getPaymentInformationUnstructured(paymentData));

    payload.put("preselected_locale", getLanguage());
    payload.put("exp", clock.instant().getEpochSecond() + 600);
    payload.put("checkout_first_name", person.getFirstName());
    payload.put("checkout_last_name", person.getLastName());
    payload.put("preselected_aspsp", paymentChannelConfiguration.getBic());

    JWSObject jwsObject = getSignedJws(payload, paymentChannelConfiguration);
    URL url = getUrl(jwsObject);

    return new PaymentLink(url.toString());
  }

  private static String getPaymentInformationUnstructured(PaymentData paymentData) {
    return (paymentData.getType() == PaymentType.MEMBER_FEE)
        ? String.format("member:%s", paymentData.getRecipientPersonalCode())
        : String.format("30101119828, IK:%s, EE3600001707", paymentData.getRecipientPersonalCode());
  }

  private String getPaymentSuccessReturnUrl(PaymentType paymentType) {
    if (paymentType == PaymentType.MEMBER_FEE) {
      return apiUrl + "/payments/member-success";
    } else {
      return apiUrl + "/payments/success";
    }
  }

  private BigDecimal getPaymentAmount(PaymentData paymentData) {
    if (paymentData.getType() == PaymentType.MEMBER_FEE) {
      if (memberFee == null) {
        throw new IllegalArgumentException("Member fee must not be null");
      }
      if (Objects.equals(paymentData.getRecipientPersonalCode(), memberFeeTestPersonalCode)
          && memberFeeTestPersonalCode != null) {
        return BigDecimal.ONE;
      }
      return memberFee;
    } else {
      if (paymentData.getAmount() == null) {
        throw new IllegalArgumentException("Payment amount must not be null");
      }
      return paymentData.getAmount();
    }
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
      Map<String, Object> payload, PaymentProviderChannel paymentChannelConfiguration) {
    JWSObject jwsObject = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(payload));
    jwsObject.sign(new MACSigner(paymentChannelConfiguration.secretKey.getBytes()));
    return jwsObject;
  }
}
