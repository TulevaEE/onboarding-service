package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.mandate.batch.MandateBatchController.MANDATE_BATCHES_URI;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.signature.SignatureController;
import ee.tuleva.onboarding.signature.response.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.response.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.signature.response.MobileSignatureResponse;
import ee.tuleva.onboarding.signature.response.MobileSignatureStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/v1" + MANDATE_BATCHES_URI)
@RequiredArgsConstructor
public class MandateBatchController implements SignatureController<Long> {
  public static final String MANDATE_BATCHES_URI = "/mandate-batches";

  private final MandateBatchService mandateBatchService;
  private final MandateBatchSignatureService mandateBatchSignatureService;

  @Operation(summary = "Create mandate batch")
  @PostMapping()
  public MandateBatchDto createMandateBatch(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody MandateBatchDto mandateBatchDto) {

    return MandateBatchDto.from(
        mandateBatchService.createMandateBatch(authenticatedPerson, mandateBatchDto));
  }

  @Override
  @Operation(summary = "Start signing mandate batch with Smart ID")
  public MobileSignatureResponse startSmartIdSignature(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return mandateBatchSignatureService.startSmartIdSignature(mandateBatchId, authenticatedPerson);
  }

  @Override
  @Operation(summary = "Is mandate batch successfully signed with Smart ID")
  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return mandateBatchSignatureService.getSmartIdSignatureStatus(
        mandateBatchId, authenticatedPerson);
  }

  @Override
  @Operation(summary = "Start signing mandate batch with ID card")
  public IdCardSignatureResponse startIdCardSignature(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody StartIdCardSignCommand signCommand) {
    return mandateBatchSignatureService.startIdCardSign(
        mandateBatchId, authenticatedPerson, signCommand);
  }

  // TODO: split this into PUT and GET endpoints
  // Currently first call persists signed hex, and later polling calls just check if mandates have
  // been processsed
  @Override
  @Operation(
      summary = "Persist ID-card signed mandate batch, and check if mandate batch is processed")
  public IdCardSignatureStatusResponse persistIdCardSignedHashOrGetSignatureStatus(
      @PathVariable("id") Long mandateBatchId,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return mandateBatchSignatureService.persistIdCardSignedHashAndGetProcessingStatus(
        mandateBatchId, signCommand, authenticatedPerson);
  }

  @Override
  @Operation(summary = "Start signing mandate batch with mobile ID")
  public MobileSignatureResponse startMobileIdSignature(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return mandateBatchSignatureService.startMobileIdSignature(mandateBatchId, authenticatedPerson);
  }

  @Override
  @Operation(summary = "Is mandate batch successfully signed with mobile ID")
  public MobileSignatureStatusResponse getMobileIdSignatureStatus(
      @PathVariable("id") Long mandateBatchId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return mandateBatchSignatureService.getMobileIdSignatureStatus(
        mandateBatchId, authenticatedPerson);
  }
}
