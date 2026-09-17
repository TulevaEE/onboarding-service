package ee.tuleva.onboarding.payment.savings;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.locale.LocaleService;
import ee.tuleva.onboarding.payment.AnonymousPayment;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentLinkGenerator;
import ee.tuleva.onboarding.payment.RedirectLink;
import ee.tuleva.onboarding.payment.provider.PaymentInternalReferenceService;
import ee.tuleva.onboarding.payment.provider.montonio.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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
    return buildPaymentLink(paymentData, person);
  }

  /**
   * The same payment, minted for somebody who never logged in.
   *
   * <p>This is what a gift link uses. Nothing about routing the money depends on knowing the payer:
   * the description and the merchant reference both name the recipient, and the callback attaches
   * the payment to them. Who paid is learned afterwards from the bank, if the bank says.
   */
  public AnonymousPayment getAnonymousPaymentLink(PaymentData paymentData) {
    var description = describe(paymentData);
    return new AnonymousPayment(buildPaymentLink(paymentData, null, description), description);
  }

  private PaymentLink buildPaymentLink(PaymentData paymentData, @Nullable Person person) {
    return buildPaymentLink(paymentData, person, describe(paymentData));
  }

  private String describe(PaymentData paymentData) {
    return String.format(
        "%s, %d", paymentData.getRecipientPersonalCode(), clock.instant().getEpochSecond());
  }

  private PaymentLink buildPaymentLink(
      PaymentData paymentData, @Nullable Person person, String description) {
    if (paymentData.getPaymentChannel() == null) {
      throw new ErrorsResponseException(
          ErrorsResponse.ofSingleError(
              "payment.channel.required", "Payment channel is required for savings payments."));
    }
    var channel =
        paymentChannelConfiguration.getPaymentProviderChannel(paymentData.getPaymentChannel());
    if (channel == null || channel.getBic() == null) {
      throw new IllegalArgumentException(
          "Invalid payment channel: " + paymentData.getPaymentChannel());
    }
    var bic = channel.getBic();
    var amount = paymentData.getAmount();
    if (amount == null || amount.compareTo(MIN_AMOUNT) < 0) {
      throw new IllegalArgumentException("Amount must be at least " + MIN_AMOUNT);
    }
    var currency = paymentData.getCurrency();
    if (currency == null || !currency.equals(Currency.EUR)) {
      throw new IllegalArgumentException("Invalid currency: " + currency);
    }
    var order = buildOrder(paymentData, person, bic, amount, currency, description);
    var url = orderClient.getPaymentUrl(order, savingsChannelConfiguration);
    return new RedirectLink(url);
  }

  // The payer is null for a gift link, where nobody logged in. Everything Montonio needs to route
  // the money comes from the recipient and the channel, so the order is complete without them.
  private MontonioOrder buildOrder(
      PaymentData paymentData,
      @Nullable Person person,
      String bic,
      BigDecimal amount,
      Currency currency,
      String description) {
    var now = clock.instant();

    return MontonioOrder.builder()
        .accessKey(savingsChannelConfiguration.getAccessKey())
        .merchantReference(
            paymentInternalReferenceService.getPaymentReference(person, paymentData, description))
        .returnUrl(savingsChannelConfiguration.getReturnUrl())
        .notificationUrl(savingsChannelConfiguration.getNotificationUrl())
        .grandTotal(amount)
        .currency(currency)
        .exp(now.getEpochSecond() + 600)
        .locale(getLanguage())
        .payment(
            MontonioOrder.MontonioPaymentMethod.builder()
                .amount(amount)
                .currency(currency)
                .methodOptions(
                    MontonioOrder.MontonioPaymentMethod.MontonioPaymentMethodOptions.builder()
                        .preferredProvider(bic)
                        .preferredLocale(getLanguage())
                        .paymentDescription(description)
                        .build())
                .build())
        .billingAddress(
            person == null
                ? null
                : MontonioOrder.MontonioBillingAddress.builder()
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
