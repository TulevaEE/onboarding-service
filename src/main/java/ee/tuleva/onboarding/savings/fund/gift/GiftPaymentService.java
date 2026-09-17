package ee.tuleva.onboarding.savings.fund.gift;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SAVINGS;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.error.ErrorsResponseException;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentService;
import java.math.BigDecimal;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GiftPaymentService {

  // Montonio will not initiate a payment above this.
  private static final BigDecimal MAX_AMOUNT = new BigDecimal("15000");
  private static final BigDecimal MIN_AMOUNT = new BigDecimal("1");

  private final GiftLinkService giftLinkService;
  private final GiftMessageRepository giftMessages;
  private final PaymentService paymentService;
  private final Clock clock;

  @Transactional
  public PaymentLink startPayment(String token, GiftPaymentRequest request) {
    var link = giftLinkService.findOpenLink(token);
    var amount = request.amount();
    if (amount == null
        || amount.compareTo(MIN_AMOUNT) < 0
        || amount.compareTo(MAX_AMOUNT) > 0
        || amount.scale() > 2) {
      // ErrorHandlingControllerAdvice does not map IllegalArgumentException, which would reach an
      // anonymous visitor as a 500.
      throw new ErrorsResponseException(
          ErrorsResponse.ofSingleError(
              "gift.amount.invalid",
              "A gift must be between " + MIN_AMOUNT + " and " + MAX_AMOUNT + " euros"));
    }

    var payment =
        paymentService.getAnonymousSavingsPaymentLink(
            PaymentData.builder()
                .recipientPersonalCode(link.getRecipientPersonalCode())
                .amount(amount)
                .currency(Currency.EUR)
                .type(SAVINGS)
                .paymentChannel(request.paymentChannel())
                .build());

    var message = trimmed(request.message());
    if (message != null) {
      giftMessages.save(
          GiftMessage.builder()
              .giftLinkId(link.getId())
              .description(payment.description())
              .amount(amount)
              .message(message)
              .createdAt(clock.instant())
              .build());
    }
    return payment.link();
  }

  private @Nullable String trimmed(@Nullable String message) {
    if (message == null || message.isBlank()) {
      return null;
    }
    var trimmed = message.strip();
    return trimmed.length() > GiftMessage.MAX_LENGTH
        ? trimmed.substring(0, GiftMessage.MAX_LENGTH)
        : trimmed;
  }
}
