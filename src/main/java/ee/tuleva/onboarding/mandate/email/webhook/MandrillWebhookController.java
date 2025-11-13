package ee.tuleva.onboarding.mandate.email.webhook;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import static org.springframework.web.bind.annotation.RequestMethod.HEAD;

@RestController
@RequestMapping("/v1/emails/webhooks")
@RequiredArgsConstructor
public class MandrillWebhookController {

  private final MandrillWebhookService webhookService;

  @RequestMapping(value = "/mandrill", method = HEAD)
  public void verifyWebhookExists() {}

  @PostMapping("/mandrill")
  @Operation(summary = "Mandrill webhook callback for email events")
  public void handleMandrillWebhook(
      @RequestParam("mandrill_events") String mandrillEvents,
      @RequestHeader(value = "X-Mandrill-Signature") String signature,
      HttpServletRequest request) {
    webhookService.handleWebhook(mandrillEvents, signature, request);
  }
}
