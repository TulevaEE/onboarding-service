package ee.tuleva.onboarding.savings.fund.gift;

import java.time.Instant;
import java.util.UUID;

public class GiftLinkFixture {

  public static final String TOKEN = "ABCDEFGH12345678";
  private static final Instant MINTED_AT = Instant.parse("2026-09-16T10:00:00Z");

  public static GiftLink anOpenLink(String childPersonalCode, String parentPersonalCode) {
    return GiftLink.builder()
        .id(UUID.randomUUID())
        .token(TOKEN)
        .recipientPersonalCode(childPersonalCode)
        .openForRecipient(childPersonalCode)
        .createdByPersonalCode(parentPersonalCode)
        .createdAt(MINTED_AT)
        .build();
  }

  public static GiftLink aReplacedLink(String childPersonalCode, String parentPersonalCode) {
    return GiftLink.builder()
        .id(UUID.randomUUID())
        .token("REPLACEDTOKEN123")
        .recipientPersonalCode(childPersonalCode)
        .createdByPersonalCode(parentPersonalCode)
        .createdAt(MINTED_AT)
        .closedAt(MINTED_AT.plusSeconds(3600))
        .build();
  }
}
