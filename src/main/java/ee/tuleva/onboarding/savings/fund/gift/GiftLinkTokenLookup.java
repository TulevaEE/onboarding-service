package ee.tuleva.onboarding.savings.fund.gift;

import ee.tuleva.onboarding.payment.GiftPayments;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class GiftLinkTokenLookup implements GiftPayments {

  private final GiftRepository gifts;
  private final GiftLinkRepository giftLinks;

  @Override
  public Optional<String> findGiftLinkToken(String paymentDescription) {
    return gifts
        .findFirstByDescription(paymentDescription)
        .flatMap(gift -> giftLinks.findById(gift.getGiftLinkId()))
        .map(GiftLink::getToken);
  }
}
