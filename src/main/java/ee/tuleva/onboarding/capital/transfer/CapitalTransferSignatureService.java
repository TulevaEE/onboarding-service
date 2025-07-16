package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import ee.tuleva.onboarding.mandate.response.IdCardSignatureResponse;
import ee.tuleva.onboarding.mandate.response.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.mandate.response.MandateSignatureStatus;
import ee.tuleva.onboarding.mandate.response.MobileSignatureResponse;
import ee.tuleva.onboarding.mandate.response.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.mandate.signature.SignatureFile;
import ee.tuleva.onboarding.mandate.signature.SignatureService;
import ee.tuleva.onboarding.mandate.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.mandate.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.mandate.signature.smartid.SmartIdSignatureSession;
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

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserId());
    List<SignatureFile> files = contractService.getSignatureFiles(contractId);

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

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserId());
    CapitalTransferContract contract = contractService.getContract(contractId);

    byte[] signedFile = signService.getSignedFile(session);

    if (signedFile != null) {
      finalizeSignature(contract, user, signedFile);
      return new MobileSignatureStatusResponse(
          MandateSignatureStatus.SIGNATURE, session.getVerificationCode());
    }

    return new MobileSignatureStatusResponse(
        MandateSignatureStatus.OUTSTANDING_TRANSACTION, session.getVerificationCode());
  }

  public IdCardSignatureResponse startIdCardSignature(
      Long contractId,
      AuthenticatedPerson authenticatedPerson,
      StartIdCardSignCommand signCommand) {

    List<SignatureFile> files = contractService.getSignatureFiles(contractId);

    IdCardSignatureSession signatureSession =
        signService.startIdCardSign(files, signCommand.getClientCertificate());

    sessionStore.save(signatureSession);

    return new IdCardSignatureResponse(signatureSession.getHashToSignInHex());
  }

  public IdCardSignatureStatusResponse persistIdCardSignedHashAndGetProcessingStatus(
      Long contractId,
      FinishIdCardSignCommand signCommand,
      AuthenticatedPerson authenticatedPerson) {

    Optional<IdCardSignatureSession> signatureSession =
        sessionStore.get(IdCardSignatureSession.class);
    IdCardSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::cardSignatureSessionNotFound);

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserId());
    CapitalTransferContract contract = contractService.getContract(contractId);

    byte[] signedFile = signService.getSignedFile(session, signCommand.getSignedHash());

    if (signedFile != null) {
      finalizeSignature(contract, user, signedFile);
      return new IdCardSignatureStatusResponse(MandateSignatureStatus.SIGNATURE);
    }

    return new IdCardSignatureStatusResponse(MandateSignatureStatus.OUTSTANDING_TRANSACTION);
  }

  public MobileSignatureResponse startMobileIdSignature(
      Long contractId, AuthenticatedPerson authenticatedPerson) {

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserId());
    List<SignatureFile> files = contractService.getSignatureFiles(contractId);

    MobileIdSignatureSession signatureSession =
        signService.startMobileIdSign(
            files, user.getPersonalCode(), authenticatedPerson.getAttribute(PHONE_NUMBER));
    sessionStore.save(signatureSession);

    return new MobileSignatureResponse(signatureSession.getVerificationCode());
  }

  public MobileSignatureStatusResponse getMobileIdSignatureStatus(
      Long contractId, AuthenticatedPerson authenticatedPerson) {

    Optional<MobileIdSignatureSession> signatureSession =
        sessionStore.get(MobileIdSignatureSession.class);
    MobileIdSignatureSession session =
        signatureSession.orElseThrow(IdSessionException::mobileSignatureSessionNotFound);

    User user = userService.getByIdOrThrow(authenticatedPerson.getUserId());
    CapitalTransferContract contract = contractService.getContract(contractId);

    byte[] signedFile = signService.getSignedFile(session);

    if (signedFile != null) {
      finalizeSignature(contract, user, signedFile);
      return new MobileSignatureStatusResponse(
          MandateSignatureStatus.SIGNATURE, session.getVerificationCode());
    }

    return new MobileSignatureStatusResponse(
        MandateSignatureStatus.OUTSTANDING_TRANSACTION, session.getVerificationCode());
  }

  private void finalizeSignature(CapitalTransferContract contract, User user, byte[] signedFile) {
    if (contract.getState() == CapitalTransferContractState.CREATED
        && contract.getSeller().getUser().equals(user)) {
      contractService.signBySeller(contract.getId(), signedFile);
    } else if (contract.getState() == CapitalTransferContractState.SELLER_SIGNED
        && contract.getBuyer().getUser().equals(user)) {
      contractService.signByBuyer(contract.getId(), signedFile);
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
