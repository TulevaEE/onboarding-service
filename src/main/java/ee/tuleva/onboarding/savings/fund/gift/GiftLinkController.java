package ee.tuleva.onboarding.savings.fund.gift;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<Void> notFound() {
    return ResponseEntity.status(NOT_FOUND).build();
  }

  // The body would otherwise carry the child's personal code back to whoever asked.
  @ExceptionHandler(NotAllowedToGiftForException.class)
  public ResponseEntity<Void> forbidden() {
    return ResponseEntity.status(FORBIDDEN).build();
  }
}
