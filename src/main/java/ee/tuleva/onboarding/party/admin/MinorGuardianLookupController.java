package ee.tuleva.onboarding.party.admin;

import static ee.tuleva.onboarding.party.MinorGuardianLookupService.PAUSE_BETWEEN_QUERIES;

import ee.tuleva.onboarding.admin.AmlServiceTokenValidator;
import ee.tuleva.onboarding.party.MinorGuardianLookupRun;
import ee.tuleva.onboarding.party.MinorGuardianLookupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/admin/aml-service")
@RequiredArgsConstructor
public class MinorGuardianLookupController {

  private final AmlServiceTokenValidator amlServiceTokenValidator;
  private final MinorGuardianLookupService minorGuardianLookupService;

  @PostMapping("/minor-guardian-lookups")
  public MinorGuardianLookupRun lookUpMinorGuardians(
      @RequestHeader("Authorization") String authorization,
      @Valid @RequestBody MinorGuardianLookupRequest request) {

    amlServiceTokenValidator.validate(authorization);
    log.info("AML service started a minor guardian lookup: batchSize={}", request.batchSize());
    return minorGuardianLookupService.lookUpMinorThirdPillarHolders(
        request.requesterPersonalCode(), request.batchSize(), PAUSE_BETWEEN_QUERIES);
  }
}
