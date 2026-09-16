package ee.tuleva.onboarding.savings.fund.gift;

import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
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

/**
 * The page a grandparent opens, with no account and no login.
 *
 * <p>Deliberately says as little as possible: who the gift is for, and what to write in the payment
 * description if they would rather transfer from their own bank. It does not expose a balance, a
 * transaction history, or anything about the parent.
 */
@RestController
@RequestMapping("/v1/gift-links")
@RequiredArgsConstructor
public class PublicGiftLinkController {

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
        // The token is in the URL of a page that names a child, so it stays out of shared caches
        // and out of anything that might index it.
        .header(CACHE_CONTROL, "no-store")
        .header("X-Robots-Tag", "noindex, nofollow")
        .body(
            new PublicGiftLink(
                recipient.getFirstName() + " " + recipient.getLastName(),
                // The child's personal code, which is what the fund matches an incoming transfer
                // by. Showing it is a deliberate choice: without it there is no way to make a
                // payment from a bank Montonio does not cover, or one over the Montonio ceiling.
                link.getRecipientPersonalCode()));
  }

  @PostMapping("/{token}/payments")
  @Operation(summary = "Start paying a gift, with no account and no login")
  public ResponseEntity<PaymentLink> startPayment(
      @PathVariable String token, @Valid @RequestBody GiftPaymentRequest request) {
    return ResponseEntity.ok()
        .header(CACHE_CONTROL, "no-store")
        .body(giftPaymentService.startPayment(token, request));
  }

  /**
   * A closed link, an unknown token and a recipient we cannot name all answer the same, so nobody
   * can tell from the outside which one they hit.
   */
  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<Void> notFound() {
    return ResponseEntity.status(NOT_FOUND).header(CACHE_CONTROL, "no-store").build();
  }

  public record PublicGiftLink(String recipientName, String paymentDescription) {}
}
