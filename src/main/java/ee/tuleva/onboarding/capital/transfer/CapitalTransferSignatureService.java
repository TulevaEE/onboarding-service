package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;
import static java.util.Objects.requireNonNull;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.signature.FinishIdCardSignCommand;
import ee.tuleva.onboarding.signature.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.signature.IdSessionException;
import ee.tuleva.onboarding.signature.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.MobileSignatureResponse;
import ee.tuleva.onboarding.signature.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SignatureService;
import ee.tuleva.onboarding.signature.SignatureStateException;
import ee.tuleva.onboarding.signature.SignatureStatus;
import ee.tuleva.onboarding.signature.SmartIdSignatureSession;
import ee.tuleva.onboarding.signature.StartIdCardSignCommand;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CapitalTransferSignatureService {
  private final CapitalTransferContractService contractService;
  private final GenericSessionStore sessionStore;
  private final UserService userService;
  private final SignatureService signService;

  public MobileSignatureResponse startSmartIdSignature(
      Long contractId, AuthenticatedPerson authenticatedPerson) {

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserIdOrThrow());
    // TODO does this create new container or add container to current?
    List<SignatureFile> files = contractService.getSignatureFiles(contractId, user);

    SmartIdSignatureSession signatureSession =
        signService.startSmartIdSign(files, user.getPersonalCode());
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  public MobileSignatureStatusResponse getSmartIdSignatureStatus(
      Long contractId, AuthenticatedPerson authenticatedPerson) {

    Optional<SmartIdSignatureSession> signatureSession =
        sessionStore.get(SmartIdSignatureSession.class);
    SmartIdSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::smartIdSignatureSessionNotFound);

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserIdOrThrow());
    CapitalTransferContract contract = contractService.getContract(contractId, user);

    byte[] signedFile = signService.getSignedFile(session);

    if (signedFile != null) {
      finalizeSignature(contract, user, signedFile);
      return new MobileSignatureStatusResponse(
          SignatureStatus.SIGNATURE, session.getVerificationCode());
    }

    return new MobileSignatureStatusResponse(
        SignatureStatus.OUTSTANDING_TRANSACTION, session.getVerificationCode());
  }

  public IdCardSignatureResponse startIdCardSignature(
      Long contractId,
      AuthenticatedPerson authenticatedPerson,
      StartIdCardSignCommand signCommand) {

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserIdOrThrow());
    List<SignatureFile> files = contractService.getSignatureFiles(contractId, user);

    IdCardSignatureSession signatureSession =
        signService.startIdCardSign(
            files,
            signCommand.certificate(),
            signCommand.supportedHashFunctions(),
            user.getPersonalCode());

    sessionStore.save(signatureSession);

    return IdCardSignatureResponse.from(signatureSession);
  }

  public IdCardSignatureStatusResponse persistIdCardSignature(
      Long contractId,
      FinishIdCardSignCommand signCommand,
      AuthenticatedPerson authenticatedPerson) {

    IdCardSignatureSession session =
        sessionStore
            .get(IdCardSignatureSession.class)
            .orElseThrow(IdSessionException::cardSignatureSessionNotFound);

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserIdOrThrow());
    CapitalTransferContract contract = contractService.getContract(contractId, user);

    if (contract.isSignedBy(user)) {
      throw SignatureStateException.alreadySigned("Capital transfer contract", contractId);
    }

    byte[] signedFile = signService.getSignedFile(session, signCommand.signature());
    finalizeSignature(contract, user, signedFile);

    return new IdCardSignatureStatusResponse(SignatureStatus.SIGNATURE);
  }

  public IdCardSignatureStatusResponse getIdCardSignatureStatus(
      Long contractId, AuthenticatedPerson authenticatedPerson) {

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserIdOrThrow());
    CapitalTransferContract contract = contractService.getContract(contractId, user);

    if (!contract.isSignedBy(user)) {
      throw SignatureStateException.notSigned("Capital transfer contract", contractId);
    }
    return new IdCardSignatureStatusResponse(SignatureStatus.SIGNATURE);
  }

  public MobileSignatureResponse startMobileIdSignature(
      Long contractId, AuthenticatedPerson authenticatedPerson) {

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserIdOrThrow());
    List<SignatureFile> files = contractService.getSignatureFiles(contractId, user);

    String phoneNumber =
        requireNonNull(
            authenticatedPerson.getAttribute(PHONE_NUMBER),
            "Phone number missing: contractId=" + contractId);
    MobileIdSignatureSession signatureSession =
        signService.startMobileIdSign(files, user.getPersonalCode(), phoneNumber);
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  public MobileSignatureStatusResponse getMobileIdSignatureStatus(
      Long contractId, AuthenticatedPerson authenticatedPerson) {

    Optional<MobileIdSignatureSession> signatureSession =
        sessionStore.get(MobileIdSignatureSession.class);
    MobileIdSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::mobileSignatureSessionNotFound);

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserIdOrThrow());
    CapitalTransferContract contract = contractService.getContract(contractId, user);

    byte[] signedFile = signService.getSignedFile(session);

    if (signedFile != null) {
      finalizeSignature(contract, user, signedFile);
      return new MobileSignatureStatusResponse(
          SignatureStatus.SIGNATURE, session.getVerificationCode());
    }

    return new MobileSignatureStatusResponse(
        SignatureStatus.OUTSTANDING_TRANSACTION, session.getVerificationCode());
  }

  private void finalizeSignature(CapitalTransferContract contract, User user, byte[] signedFile) {
    if (contract.getState() == CapitalTransferContractState.CREATED
        && contract.getSeller().getUser().equals(user)) {
      contractService.signBySeller(contract.getId(), signedFile, user);
    } else if (contract.getState() == CapitalTransferContractState.SELLER_SIGNED
        && contract.getBuyer().getUser().equals(user)) {
      contractService.signByBuyer(contract.getId(), signedFile, user);
    } else {
      log.error(
          "Cannot sign contract {} in state {} by user {}",
          contract.getId(),
          contract.getState(),
          user.getId());
      throw new IllegalStateException("Cannot sign contract in its current state");
    }
  }
}
