package ee.tuleva.onboarding.kyb.admin;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.kyb.KybCheckOverrideService;
import ee.tuleva.onboarding.kyb.KybCheckType;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Profile("!staging")
public class KybAdminController {

  private final AdminTokenValidator tokenValidator;
  private final KybCheckOverrideService kybCheckOverrideService;
  private final Clock clock;

  @PostMapping("/override-kyb-check")
  public String overrideKybCheck(
      @RequestHeader("X-Admin-Token") String token,
      @RequestParam String registryCode,
      @RequestParam KybCheckType checkType,
      @RequestParam String reason,
      @RequestParam(required = false) @Nullable Instant expiresAt) {

    tokenValidator.validateWithOpsAccess(token);
    if (!checkType.isManuallyForceable()) {
      throw new ResponseStatusException(
          BAD_REQUEST, "Check is not manually forceable: " + checkType);
    }
    if (reason.isBlank()) {
      throw new ResponseStatusException(BAD_REQUEST, "A reason is required");
    }
    if (expiresAt != null && !expiresAt.isAfter(clock.instant())) {
      throw new ResponseStatusException(BAD_REQUEST, "Expiry must be in the future: " + expiresAt);
    }
    log.info(
        "Admin overriding KYB check: registryCode={}, checkType={}, reason={}",
        registryCode,
        checkType,
        reason);
    if (expiresAt == null) {
      kybCheckOverrideService.forceSuccess(registryCode, checkType, reason);
    } else {
      kybCheckOverrideService.forceSuccess(registryCode, checkType, reason, expiresAt);
    }

    return "Saved KYB check override: registryCode=" + registryCode + ", checkType=" + checkType;
  }
}
