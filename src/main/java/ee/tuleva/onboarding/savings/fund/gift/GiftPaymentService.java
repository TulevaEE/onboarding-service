package ee.tuleva.onboarding.savings.fund.gift;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SAVINGS;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.PaymentService;
import java.math.BigDecimal;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns a gift link plus an amount into a Montonio payment, for a visitor with no account. */
@Service
@RequiredArgsConstructor
public class GiftPaymentService {

  // Montonio will not initiate above this, and the manual transfer details are offered instead.
  // Enforced here as well as in the browser, because the browser is not where the rule lives.
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
      throw new IllegalArgumentException(
          "A gift must be between " + MIN_AMOUNT + " and " + MAX_AMOUNT + " euros");
    }

    var payment =
        paymentService.getAnonymousSavingsPaymentLink(
            PaymentData.builder()
                // The gift is addressed to the child, and the payment carries no claim about who
                // sent it. Whether a stranger may pay into this account is decided when the money
                // lands, not here.
                .recipientPersonalCode(link.getRecipientPersonalCode())
                .amount(amount)
                .currency(Currency.EUR)
                .type(SAVINGS)
                .paymentChannel(request.paymentChannel())
                .build());

    // Written now rather than on the callback, because this is the only moment the giver is here
    // to have written it. An abandoned payment leaves a message nothing ever joins to, which the
    // parent's page never sees, since it starts from payments that arrived.
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
