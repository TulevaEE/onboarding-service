package ee.tuleva.onboarding.payment.provider.montonio;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentData.PaymentType;
import ee.tuleva.onboarding.payment.provider.PaymentInternalReferenceService;
import ee.tuleva.onboarding.payment.provider.PaymentProviderChannel;
import ee.tuleva.onboarding.payment.provider.PaymentProviderConfiguration;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MontonioOrderCreator {

  private final Clock clock;

  private final PaymentInternalReferenceService paymentInternalReferenceService;

  private final PaymentProviderConfiguration paymentProviderConfiguration;

  private final LocaleService localeService;

  private final Environment environment;


  @Value("${api.url}")
  private String apiUrl;

  @Value("${payment.member-fee}")
  private BigDecimal memberFee;

  @Value("${payment.member-fee-test-personal-code}")
  private String memberFeeTestPersonalCode;

  private static String getEpisPaymentDescription(PaymentData paymentData) {
    return (paymentData.getType() == PaymentType.MEMBER_FEE)
        ? String.format("member:%s", paymentData.getRecipientPersonalCode())
        : String.format(
        "30101119828, IK:%s, EE3600001707",
        paymentData
            .getRecipientPersonalCode()); // https://www.pensionikeskus.ee/iii-sammas/sissemaksed/sissemaksed-iii-samba-fondidesse/
  }

  @SneakyThrows
  public MontonioOrder getOrder(PaymentData paymentData, Person person) {
    PaymentProviderChannel paymentChannelConfiguration =
        paymentProviderConfiguration.getPaymentProviderChannel(paymentData.getPaymentChannel());

    BigDecimal amount = getPaymentAmount(paymentData);
    Currency currency = paymentData.getCurrency();

    // TODO first name, last name to order billing address?
    // payload.put("checkout_first_name", person.getFirstName());
    // payload.put("checkout_last_name", person.getLastName());
    return MontonioOrder.builder()
        .accessKey(paymentChannelConfiguration.getAccessKey())
        .merchantReference(paymentInternalReferenceService.getPaymentReference(person, paymentData))
        .returnUrl(getPaymentSuccessReturnUrl(paymentData.getType()))
        .notificationUrl(getNotificationUrl())
        .grandTotal(amount)
        .currency(currency)
        .exp(clock.instant().getEpochSecond() + 600)
        .locale(getLanguage())
        .payment(
            MontonioPaymentMethod.builder()
                .amount(amount)
                .currency(currency)
                .methodOptions(
                    MontonioPaymentMethodOptions.builder()
                        .preferredProvider(paymentChannelConfiguration.getBic())
                        .preferredLocale(getLanguage())
                        .paymentDescription(getEpisPaymentDescription(paymentData))
                        .build())
                .build())
        .build();
  }

  private String getPaymentSuccessReturnUrl(PaymentType paymentType) {
    if (paymentType == PaymentType.MEMBER_FEE) {
      return apiUrl + "/payments/member-success";
    } else {
      return apiUrl + "/payments/success";
    }
  }

  private BigDecimal getPaymentAmount(PaymentData paymentData) {
    if (paymentData.getType() == PaymentData.PaymentType.MEMBER_FEE) {
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
    return Locale.ENGLISH.getLanguage().equals(locale.getLanguage()) ? "en" : locale.getLanguage();
  }


  private String getNotificationUrl() {
    if (Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
      // Montonio doesn't support localhost notification urls
      return "https://tuleva.ee/fake-return-url";
    }

    return apiUrl + "/payments/notification";
  }
}
