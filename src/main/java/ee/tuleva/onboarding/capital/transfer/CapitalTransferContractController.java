package ee.tuleva.onboarding.capital.transfer;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.signature.SignatureController;
import ee.tuleva.onboarding.signature.response.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.response.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.signature.response.MobileSignatureResponse;
import ee.tuleva.onboarding.signature.response.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/capital-transfer-contracts")
@RequiredArgsConstructor
public class CapitalTransferContractController implements SignatureController<Long> {

  private final CapitalTransferContractService contractService;
  private final CapitalTransferSignatureService signatureService;
  private final UserService userService;

  @Operation(summary = "Create a capital transfer contract")
  @PostMapping
  public CapitalTransferContractDto createContract(
      @AuthenticationPrincipal AuthenticatedPerson seller,
      @Valid @RequestBody CreateCapitalTransferContractCommand command) {
    CapitalTransferContract contract = contractService.create(seller, command);
    return CapitalTransferContractDto.from(contract);
  }

  @Operation(summary = "Get a capital transfer contract by ID")
  @GetMapping("/{id}")
  public CapitalTransferContractDto getContract(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    var user = userService.getByIdOrThrow(authenticatedPerson.getUserId());
    return CapitalTransferContractDto.from(contractService.getContract(id, user));
  }

  @Operation(summary = "Get my capital transfer contracts")
  @GetMapping
  public List<CapitalTransferContractDto> getContracts(
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    var user = userService.getByIdOrThrow(authenticatedPerson.getUserId());
    return contractService.getMyContracts(user).stream()
        .map(CapitalTransferContractDto::from)
        .toList();
  }

  @Operation(summary = "Update the state of a capital transfer contract")
  @PatchMapping("/{id}")
  public CapitalTransferContractDto updateContractState(
      @PathVariable Long id,
      @Valid @RequestBody UpdateCapitalTransferContractStateCommand command,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return CapitalTransferContractDto.from(
        contractService.updateStateByUser(
            id, command.getState(), userService.getByIdOrThrow(authenticatedPerson.getUserId())));
  }

  @Override
  @Operation(summary = "Start Smart-ID signing for a capital transfer contract")
  public MobileSignatureResponse startSmartIdSignature(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return signatureService.startSmartIdSignature(id, authenticatedPerson);
  }

  @Override
  @Operation(summary = "Get Smart-ID signing status for a capital transfer contract")
  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return signatureService.getSmartIdSignatureStatus(id, authenticatedPerson);
  }

  @Override
  @Operation(summary = "Start ID Card signing for a capital transfer contract")
  public IdCardSignatureResponse startIdCardSignature(
      @PathVariable Long id,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
      @Valid @RequestBody StartIdCardSignCommand signCommand) {
    return signatureService.startIdCardSignature(id, authenticatedPerson, signCommand);
  }

  @Override
  @Operation(
      summary =
          "Persist ID Card signed hash and get signing status for a capital transfer contract")
  public IdCardSignatureStatusResponse persistIdCardSignedHashOrGetSignatureStatus(
      @PathVariable Long id,
      @Valid @RequestBody FinishIdCardSignCommand signCommand,
      @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return signatureService.persistIdCardSignedHashAndGetProcessingStatus(
        id, signCommand, authenticatedPerson);
  }

  @Override
  @Operation(summary = "Start Mobile ID signing for a capital transfer contract")
  public MobileSignatureResponse startMobileIdSignature(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return signatureService.startMobileIdSignature(id, authenticatedPerson);
  }

  @Override
  @Operation(summary = "Get Mobile ID signing status for a capital transfer contract")
  public MobileSignatureStatusResponse getMobileIdSignatureStatus(
      @PathVariable Long id, @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson) {
    return signatureService.getMobileIdSignatureStatus(id, authenticatedPerson);
  }
}
