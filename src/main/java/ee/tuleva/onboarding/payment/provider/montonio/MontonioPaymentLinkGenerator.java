package ee.tuleva.onboarding.payment.provider.montonio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.currency.Currency;
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

import ee.tuleva.onboarding.payment.provider.PaymentInternalReferenceService;
import ee.tuleva.onboarding.payment.provider.PaymentProviderChannel;
import ee.tuleva.onboarding.payment.provider.PaymentProviderConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MontonioPaymentLinkGenerator implements PaymentLinkGenerator {

  private final ObjectMapper objectMapper;

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


  @SneakyThrows
  @Override
  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    //TODO: implement new JSON token for Order API
    PaymentProviderChannel paymentChannelConfiguration =
        paymentProviderConfiguration.getPaymentProviderChannel(paymentData.getPaymentChannel());

    BigDecimal amount = getPaymentAmount(paymentData);
    Currency currency = paymentData.getCurrency();


    MontonioOrder order = MontonioOrder.builder()
        .accessKey(paymentChannelConfiguration.getAccessKey())
        .merchantReference(paymentInternalReferenceService.getPaymentReference(person, paymentData))
        .returnUrl(getPaymentSuccessReturnUrl(paymentData.getType()))
        .notificationUrl(apiUrl + "/payments/notification")
        .grandTotal(amount)
        .currency(currency)
        .exp(clock.instant().getEpochSecond() + 600)
        .payment(MontonioPaymentMethod.builder()
            .amount(amount)
            .currency(currency)
            .methodOptions(
                MontonioPaymentMethodOptions.builder()
                    .preferredProvider(paymentChannelConfiguration.getBic())
                    .preferredLocale(getLanguage())
                    .build()
            )
            .build()
        )
        .build();

    // TODO first name, last name to order billing address?
    // payload.put("checkout_first_name", person.getFirstName());
    // payload.put("checkout_last_name", person.getLastName());


    // TODO api call to montonio, submit order token to get paymentLink
    JWSObject jwsObject = getSignedJws(objectMapper.writeValueAsString(order), paymentChannelConfiguration);
    URL url = getUrl(jwsObject);

    return new PaymentLink(url.toString());
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
        ? "en"
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
      String payload, PaymentProviderChannel paymentChannelConfiguration) {
    JWSObject jwsObject = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(payload));
    jwsObject.sign(new MACSigner(paymentChannelConfiguration.getSecretKey().getBytes()));
    return jwsObject;
  }
}
