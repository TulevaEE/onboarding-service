package ee.tuleva.onboarding.savings.fund.gift;

import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import ee.tuleva.onboarding.payment.PaymentLink;
import ee.tuleva.onboarding.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/gift-links")
@RequiredArgsConstructor
public class PublicGiftLinkController {

  // The token sits in the URL of a page that names a child, so no shared cache and no index.
  private static final String NO_STORE = "no-store";
  private static final String NO_INDEX = "noindex, nofollow";

  private final GiftLinkService giftLinkService;
  private final GiftPaymentService giftPaymentService;
  private final UserService userService;

  @GetMapping("/{token}")
  @Operation(summary = "What an anonymous visitor may see about a gift link")
  public ResponseEntity<PublicGiftLink> viewLink(@PathVariable String token) {
    var link = giftLinkService.findOpenLink(token);
    var recipient =
        userService
            .findByPersonalCode(link.getRecipientPersonalCode())
            .orElseThrow(() -> new NoSuchElementException("No such gift link"));
    return ResponseEntity.ok()
        .header(CACHE_CONTROL, NO_STORE)
        .header("X-Robots-Tag", NO_INDEX)
        .body(
            new PublicGiftLink(
                fullName(recipient.getFirstName(), recipient.getLastName()),
                paymentDescriptionFor(link)));
  }

  @PostMapping("/{token}/payments")
  @Operation(summary = "Start paying a gift, with no account and no login")
  public ResponseEntity<PaymentLink> startPayment(
      @PathVariable String token, @Valid @RequestBody GiftPaymentRequest request) {
    return ResponseEntity.ok()
        .header(CACHE_CONTROL, NO_STORE)
        .body(giftPaymentService.startPayment(token, request));
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<Void> notFound() {
    return ResponseEntity.status(NOT_FOUND).header(CACHE_CONTROL, NO_STORE).build();
  }

  @ExceptionHandler(NotAllowedToGiftForException.class)
  public ResponseEntity<Void> forbidden() {
    return ResponseEntity.status(FORBIDDEN).header(CACHE_CONTROL, NO_STORE).build();
  }

  private static String fullName(String firstName, String lastName) {
    return firstName + " " + lastName;
  }

  private static String paymentDescriptionFor(GiftLink link) {
    return link.getRecipientPersonalCode();
  }

  public record PublicGiftLink(String recipientName, String paymentDescription) {}
}
