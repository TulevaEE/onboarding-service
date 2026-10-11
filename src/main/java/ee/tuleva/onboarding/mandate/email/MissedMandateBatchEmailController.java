package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admin/missed-mandate-batch-emails")
@RequiredArgsConstructor
@Profile("!staging")
public class MissedMandateBatchEmailController {

  private final AdminTokenValidator tokenValidator;
  private final MissedMandateBatchEmails missedMandateBatchEmails;

  @GetMapping
  public List<Long> find(@RequestHeader("X-Admin-Token") String token, @RequestParam int days) {
    tokenValidator.validate(token);
    return missedMandateBatchEmails.find(days);
  }

  @PostMapping("/resend")
  public MissedEmailResend resend(
      @RequestHeader("X-Admin-Token") String token, @RequestParam int days) {
    tokenValidator.validate(token);
    log.info("Admin triggered missed mandate batch email resend: days={}", days);
    return missedMandateBatchEmails.resend(days);
  }
}
