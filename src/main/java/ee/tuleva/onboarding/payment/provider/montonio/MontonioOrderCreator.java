package ee.tuleva.onboarding.payment.provider.montonio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSObject;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.provider.PaymentInternalReferenceService;
import ee.tuleva.onboarding.payment.provider.PaymentProviderChannel;
import ee.tuleva.onboarding.payment.provider.PaymentProviderConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URL;
import java.time.Clock;

@Service
@RequiredArgsConstructor
@Slf4j
public class MontonioOrderCreator {


  private final ObjectMapper objectMapper;

  private final Clock clock;

  private final PaymentInternalReferenceService paymentInternalReferenceService;

  private final PaymentProviderConfiguration paymentProviderConfiguration;


  @Value("${api.url}")
  private String apiUrl;

  @Value("${payment.member-fee}")
  private BigDecimal memberFee;



  @SneakyThrows
  @Override
  private String getOrderString(PaymentData paymentData, Person person) {
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
}
