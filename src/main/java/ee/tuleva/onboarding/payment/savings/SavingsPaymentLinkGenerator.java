package ee.tuleva.onboarding.payment.savings;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentLinkGenerator;
import ee.tuleva.onboarding.payment.provider.PaymentInternalReferenceService;
import ee.tuleva.onboarding.payment.provider.montonio.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsPaymentLinkGenerator implements PaymentLinkGenerator {

  private final Clock clock;
  private final MontonioOrderClient orderClient;
  private final SavingsChannelConfiguration savingsChannelConfiguration;
  private final MontonioPaymentChannelConfiguration paymentChannelConfiguration;
  private final PaymentInternalReferenceService paymentInternalReferenceService;
  private final LocaleService localeService;

  private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

  @Override
  public PaymentLink getPaymentLink(PaymentData paymentData, Person person) {
    var bic =
        paymentChannelConfiguration
            .getPaymentProviderChannel(paymentData.getPaymentChannel())
            .getBic();
    if (bic == null) {
      throw new IllegalArgumentException(
          "Invalid payment channel: " + paymentData.getPaymentChannel());
    }
    if (paymentData.getAmount() == null || paymentData.getAmount().compareTo(MIN_AMOUNT) < 0) {
      throw new IllegalArgumentException("Amount must be at least " + MIN_AMOUNT);
    }
    if (paymentData.getCurrency() == null || !paymentData.getCurrency().equals(Currency.EUR)) {
      throw new IllegalArgumentException("Invalid currency: " + paymentData.getCurrency());
    }
    var order = buildOrder(paymentData, person, bic);
    var url = orderClient.getPaymentUrl(order, savingsChannelConfiguration);
    return new PaymentLink(url);
  }

  private MontonioOrder buildOrder(PaymentData paymentData, Person person, String bic) {
    var now = clock.instant();
    var description =
        String.format("%s, %d", paymentData.getRecipientPersonalCode(), now.getEpochSecond());

    return MontonioOrder.builder()
        .accessKey(savingsChannelConfiguration.getAccessKey())
        .merchantReference(
            paymentInternalReferenceService.getPaymentReference(person, paymentData, description))
        .returnUrl(savingsChannelConfiguration.getReturnUrl())
        .notificationUrl(savingsChannelConfiguration.getNotificationUrl())
        .grandTotal(paymentData.getAmount())
        .currency(paymentData.getCurrency())
        .exp(now.getEpochSecond() + 600)
        .locale(getLanguage())
        .payment(
            MontonioOrder.MontonioPaymentMethod.builder()
                .amount(paymentData.getAmount())
                .currency(paymentData.getCurrency())
                .methodOptions(
                    MontonioOrder.MontonioPaymentMethod.MontonioPaymentMethodOptions.builder()
                        .preferredProvider(bic)
                        .preferredLocale(getLanguage())
                        .paymentDescription(description)
                        .build())
                .build())
        .billingAddress(
            MontonioOrder.MontonioBillingAddress.builder()
                .firstName(person.getFirstName())
                .lastName(person.getLastName())
                .build())
        .build();
  }

  private String getLanguage() {
    Locale locale = localeService.getCurrentLocale();
    return Locale.ENGLISH.getLanguage().equals(locale.getLanguage()) ? "en" : locale.getLanguage();
  }
}
