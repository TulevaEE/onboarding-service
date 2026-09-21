package ee.tuleva.onboarding.savings.fund.gift;

import static org.assertj.core.api.Assertions.assertThat;

import ee.tuleva.onboarding.payment.GiftPayments;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(GiftLinkTokenLookup.class)
class GiftLinkTokenLookupDatabaseTest {

  private static final Instant NOW = Instant.parse("2026-09-17T10:00:00Z");
  private static final String PARENT = "38888888888";
  private static final String CHILD = "61001010000";
  private static final String TOKEN = "9TY0PX9JVWKMBD3R";
  private static final String DESCRIPTION = "61001010000, 1758012345, BCDFGH";

  @Autowired private GiftPayments giftPayments;
  @Autowired private GiftLinkRepository giftLinks;
  @Autowired private GiftRepository gifts;

  @Test
  void aPaymentStartedThroughAGiftLinkNamesThatLink() {
    aGiftStartedThrough(anOpenLink());

    assertThat(giftPayments.findGiftLinkToken(DESCRIPTION)).contains(TOKEN);
  }

  @Test
  void aPaymentStartedThroughALinkTheParentHasSinceReplacedStillNamesThatLink() {
    aGiftStartedThrough(aClosedLink());

    assertThat(giftPayments.findGiftLinkToken(DESCRIPTION)).contains(TOKEN);
  }

  @Test
  void aPaymentThatWasNotStartedThroughAGiftLinkNamesNoLink() {
    aGiftStartedThrough(anOpenLink());

    assertThat(giftPayments.findGiftLinkToken("61001010000, 1758012345, XYZWQR")).isEmpty();
  }

  private GiftLink anOpenLink() {
    return giftLinks.save(
        GiftLink.builder()
            .token(TOKEN)
            .recipientPersonalCode(CHILD)
            .openForRecipient(CHILD)
            .createdByPersonalCode(PARENT)
            .createdAt(NOW)
            .build());
  }

  private GiftLink aClosedLink() {
    var link = anOpenLink();
    link.close(NOW);
    return giftLinks.save(link);
  }

  private void aGiftStartedThrough(GiftLink link) {
    gifts.save(
        Gift.builder()
            .giftLinkId(link.getId())
            .description(DESCRIPTION)
            .amount(new BigDecimal("100.00"))
            .createdAt(NOW)
            .build());
  }
}
