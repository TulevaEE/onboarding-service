package ee.tuleva.onboarding.savings.fund.gift;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The parent's side of a gift link.
 *
 * <p>Both endpoints act on the child the caller is currently representing, taken from their role
 * rather than from the request body, so a caller cannot ask for a link to a child they are not
 * acting as.
 */
@RestController
@RequestMapping("/v1/savings-fund/gift-links")
@RequiredArgsConstructor
public class GiftLinkController {

  private final GiftLinkService giftLinkService;
  private final ReceivedGiftService receivedGiftService;

  @GetMapping("/gifts")
  @Operation(summary = "Gifts that have arrived for the child being represented")
  public List<ReceivedGift> receivedGifts(@AuthenticationPrincipal AuthenticatedPerson person) {
    return receivedGiftService.receivedGifts(person.getPersonalCode(), person.getRoleCode());
  }

  @PostMapping
  @Operation(summary = "Get or create the gift link for the child being represented")
  public GiftLinkResponse openLink(@AuthenticationPrincipal AuthenticatedPerson person) {
    return GiftLinkResponse.of(
        giftLinkService.openLinkFor(person.getPersonalCode(), person.getRoleCode()));
  }

  @PostMapping("/{id}/replace")
  @Operation(summary = "Close this gift link and issue a new one")
  public GiftLinkResponse replaceLink(
      @AuthenticationPrincipal AuthenticatedPerson person, @PathVariable UUID id) {
    return GiftLinkResponse.of(giftLinkService.replaceLink(person.getPersonalCode(), id));
  }
}
