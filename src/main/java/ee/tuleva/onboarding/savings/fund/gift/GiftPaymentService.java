package ee.tuleva.onboarding.savings.fund.gift;

import static ee.tuleva.onboarding.payment.PaymentData.PaymentType.SAVINGS;

import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.payment.PaymentData;
import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.payment.savings.SavingsPaymentLinkGenerator;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Turns a gift link plus an amount into a Montonio payment, for a visitor with no account. */
@Service
@RequiredArgsConstructor
public class GiftPaymentService {

  // Montonio will not initiate above this, and the manual transfer details are offered instead.
  // Enforced here as well as in the browser, because the browser is not where the rule lives.
  private static final BigDecimal MAX_AMOUNT = new BigDecimal("15000");
  private static final BigDecimal MIN_AMOUNT = new BigDecimal("1");

  private final GiftLinkService giftLinkService;
  private final SavingsPaymentLinkGenerator paymentLinkGenerator;

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
    return paymentLinkGenerator.getAnonymousPaymentLink(
        PaymentData.builder()
            // The gift is addressed to the child, and the payment carries no claim about who sent
            // it. Whether a stranger may pay into this account is decided when the money lands,
            // not here.
            .recipientPersonalCode(link.getRecipientPersonalCode())
            .amount(amount)
            .currency(Currency.EUR)
            .type(SAVINGS)
            .paymentChannel(request.paymentChannel())
            .build());
  }
}
