package ee.tuleva.onboarding.payment.provider.montonio;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentData.PaymentType;
import ee.tuleva.onboarding.payment.provider.PaymentInternalReferenceService;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioOrder.MontonioBillingAddress;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioOrder.MontonioPaymentMethod;
import ee.tuleva.onboarding.payment.provider.montonio.MontonioOrder.MontonioPaymentMethod.MontonioPaymentMethodOptions;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MontonioOrderCreator {

  private final Clock clock;

  private final PaymentInternalReferenceService paymentInternalReferenceService;

  private final MontonioPaymentChannelConfiguration montonioPaymentChannelConfiguration;

  private final LocaleService localeService;

  @Value("${payment-provider.use-fake-notification-url}")
  private Boolean useFakeNotificationsUrl;

  @Value("${api.url}")
  private String apiUrl;

  // TODO: can we extract knowledge about member fee / EPIS out of this class?
  @Value("${payment.member-fee}")
  private BigDecimal memberFee;

  @Value("${payment.member-fee-test-personal-code}")
  private String memberFeeTestPersonalCode;

  // TODO: Move this method to PaymentData class?
  private static String getPaymentDescription(PaymentData paymentData) {
    if (paymentData.getType() == PaymentType.MEMBER_FEE) {
      return String.format("member:%s", paymentData.getRecipientPersonalCode());
    }

    final var episPaymentDescription = "30101119828, IK:%s, EE3600001707";

    return String.format(episPaymentDescription, paymentData.getRecipientPersonalCode());
  }

  MontonioOrder getOrder(PaymentData paymentData, Person person) {
    MontonioPaymentChannel paymentChannelConfiguration =
        montonioPaymentChannelConfiguration.getPaymentProviderChannel(
            paymentData.getPaymentChannel());

    BigDecimal amount = getPaymentAmount(paymentData);
    Currency currency = paymentData.getCurrency();
    String description = getPaymentDescription(paymentData);

    return MontonioOrder.builder()
        .accessKey(paymentChannelConfiguration.getAccessKey())
        .merchantReference(
            paymentInternalReferenceService.getPaymentReference(person, paymentData, description))
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
                        .paymentDescription(description)
                        .build())
                .build())
        .billingAddress(
            MontonioBillingAddress.builder()
                .firstName(person.getFirstName())
                .lastName(person.getLastName())
                .build())
        .build();
  }

  private String getPaymentSuccessReturnUrl(PaymentType paymentType) {
    if (paymentType == PaymentType.MEMBER_FEE) {
      return apiUrl + "/payments/member-success";
    }

    return apiUrl + "/payments/success";
  }

  private BigDecimal getPaymentAmount(PaymentData paymentData) {
    // TODO: Use polymorphism to get payment fee?
    if (paymentData.getType() == PaymentType.MEMBER_FEE) {
      return this.getMemberPaymentAmount(paymentData);
    }

    if (paymentData.getAmount() == null) {
      throw new IllegalArgumentException("Payment amount must not be null");
    }

    return paymentData.getAmount();
  }

  private BigDecimal getMemberPaymentAmount(PaymentData paymentData) {
    if (memberFee == null) {
      throw new IllegalArgumentException("Member fee must not be null");
    }
    if (Objects.equals(paymentData.getRecipientPersonalCode(), memberFeeTestPersonalCode)
        && memberFeeTestPersonalCode != null) {
      return BigDecimal.ONE;
    }
    return memberFee;
  }

  private String getLanguage() {
    Locale locale = localeService.getCurrentLocale();
    return Locale.ENGLISH.getLanguage().equals(locale.getLanguage()) ? "en" : locale.getLanguage();
  }

  private String getNotificationUrl() {
    // TODO move boolean to string, use interpolation in yaml for production link
    if (useFakeNotificationsUrl) {
      // Montonio doesn't support localhost notification urls
      return "https://tuleva.ee/fake-notification-url";
    }

    return apiUrl + "/payments/notifications";
  }
}
