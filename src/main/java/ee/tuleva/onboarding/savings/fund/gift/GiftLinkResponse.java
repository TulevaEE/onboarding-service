package ee.tuleva.onboarding.savings.fund.gift;

import java.util.UUID;

public record GiftLinkResponse(UUID id, String token) {

  static GiftLinkResponse of(GiftLink link) {
    return new GiftLinkResponse(link.getId(), link.getToken());
  }
}
