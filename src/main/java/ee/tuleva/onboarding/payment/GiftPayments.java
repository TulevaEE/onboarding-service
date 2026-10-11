package ee.tuleva.onboarding.payment;

import java.util.Optional;

@FunctionalInterface
public interface GiftPayments {

  Optional<String> findGiftLinkToken(String paymentDescription);
}
