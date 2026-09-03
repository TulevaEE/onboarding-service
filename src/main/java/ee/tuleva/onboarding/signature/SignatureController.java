package ee.tuleva.onboarding.signature;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// useSigning in frontend
@RequestMapping("/default")
public interface SignatureController<TEntityId> {
  @Operation(summary = "Start signing entity with Smart ID")
  @PutMapping("/{id}/signature/smart-id")
  MobileSignatureResponse startSmartIdSignature(
      @PathVariable("id") TEntityId entityId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson);

  @Operation(summary = "Is entity successfully signed with Smart ID")
  @GetMapping("/{id}/signature/smart-id/status")
  MobileSignatureStatusResponse getSmartIdSignatureStatus(
      @PathVariable("id") TEntityId entityId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson);

  @Operation(summary = "Start signing entity with ID card")
  @PutMapping("/{id}/signature/id-card")
  IdCardSignatureResponse startIdCardSignature(
      @PathVariable("id") TEntityId entityId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody StartIdCardSignCommand signCommand);

  @Operation(summary = "Persist the ID card signature of the entity and start processing it")
  @PutMapping("/{id}/signature/id-card/signature")
  IdCardSignatureStatusResponse persistIdCardSignature(
      @PathVariable("id") TEntityId entityId,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson);

  @Operation(summary = "Get the ID card signing status of the entity")
  @GetMapping("/{id}/signature/id-card/status")
  IdCardSignatureStatusResponse getIdCardSignatureStatus(
      @PathVariable("id") TEntityId entityId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson);

  @Operation(summary = "Start signing entity with mobile ID")
  @PutMapping("/{id}/signature/mobile-id")
  MobileSignatureResponse startMobileIdSignature(
      @PathVariable("id") TEntityId entityId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson);

  @Operation(summary = "Is entity successfully signed with mobile ID")
  @GetMapping("/{id}/signature/mobile-id/status")
  MobileSignatureStatusResponse getMobileIdSignatureStatus(
      @PathVariable("id") TEntityId entityId,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson);
}
