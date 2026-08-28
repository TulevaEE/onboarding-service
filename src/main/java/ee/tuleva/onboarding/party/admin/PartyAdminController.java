package ee.tuleva.onboarding.party.admin;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import ee.tuleva.onboarding.admin.AdminTokenValidator;
import ee.tuleva.onboarding.party.ChildAmlBackfillResult;
import ee.tuleva.onboarding.party.ChildAmlBackfillService;
import ee.tuleva.onboarding.party.ParentChildLinkRegistrationService;
import ee.tuleva.onboarding.savings.fund.SavingsFundOnboardingService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Profile("!staging")
public class PartyAdminController {

  private final AdminTokenValidator tokenValidator;
  private final ParentChildLinkRegistrationService parentChildLinkRegistrationService;
  private final ChildAmlBackfillService childAmlBackfillService;
  private final SavingsFundOnboardingService savingsFundOnboardingService;
  private final Clock clock;

  @PostMapping("/parent-child-link")
  public String createParentChildLink(
      @RequestHeader("X-Admin-Token") String token,
      @Valid @RequestBody CreateParentChildLinkRequest request) {

    tokenValidator.validateWithOpsAccess(token);
    parentChildLinkRegistrationService.register(
        request.parentCode(),
        request.childCode(),
        request.childFirstName(),
        request.childLastName());
    savingsFundOnboardingService.seedPersonOnboardingIfAbsent(request.childCode());

    return "Created parent-child link: parentCode="
        + request.parentCode()
        + ", childCode="
        + request.childCode();
  }

  @PostMapping("/guardian-link")
  public String createGuardianLink(
      @RequestHeader("X-Admin-Token") String token,
      @Valid @RequestBody CreateGuardianLinkRequest request) {

    tokenValidator.validateWithOpsAccess(token);
    if (!request.validUntil().isAfter(LocalDate.now(clock))) {
      throw new ResponseStatusException(
          BAD_REQUEST,
          "Guardian link validUntil must be in the future: validUntil=" + request.validUntil());
    }
    parentChildLinkRegistrationService.registerGuardian(
        request.guardianCode(),
        request.wardCode(),
        request.wardFirstName(),
        request.wardLastName(),
        request.validUntil());
    savingsFundOnboardingService.seedPersonOnboardingIfAbsent(request.wardCode());

    return "Created guardian link: guardianCode="
        + request.guardianCode()
        + ", wardCode="
        + request.wardCode();
  }

  @PostMapping("/child-aml-backfill")
  public ChildAmlBackfillResult backfillChildAmlChecks(
      @RequestHeader("X-Admin-Token") String token,
      @Valid @RequestBody ChildAmlBackfillRequest request) {

    tokenValidator.validateWithOpsAccess(token);
    log.info("Admin triggered child AML backfill: dryRun={}", request.dryRun());
    return childAmlBackfillService.backfill(request.requesterPersonalCode(), request.dryRun());
  }
}
