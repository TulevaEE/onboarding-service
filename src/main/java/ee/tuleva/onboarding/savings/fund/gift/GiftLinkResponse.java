package ee.tuleva.onboarding.savings.fund.gift;

import java.util.UUID;

/** What the parent's page needs to show and share the link. */
public record GiftLinkResponse(UUID id, String token) {

  static GiftLinkResponse of(GiftLink link) {
    return new GiftLinkResponse(link.getId(), link.getToken());
  }
}
