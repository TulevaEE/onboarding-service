package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.auth.mobileid.MobileIDSession.PHONE_NUMBER;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractFixture.sampleCapitalTransferContractWithSeller;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractFixture.sampleCapitalTransferContractWithSellerAndBuyer;
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.mandate.exception.IdSessionException;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SignatureService;
import ee.tuleva.onboarding.signature.idcard.IdCardSignatureSession;
import ee.tuleva.onboarding.signature.mobileid.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.response.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.response.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.signature.response.MobileSignatureResponse;
import ee.tuleva.onboarding.signature.response.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.signature.response.SignatureStatus;
import ee.tuleva.onboarding.signature.smartid.SmartIdSignatureSession;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.member.Member;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CapitalTransferSignatureServiceTest {

  @Mock private CapitalTransferContractService contractService;
  @Mock private SignatureService signService;
  @Mock private GenericSessionStore sessionStore;
  @Mock private UserService userService;

  @InjectMocks private CapitalTransferSignatureService signatureService;

  @Test
  void startSmartIdSignature_startsSignatureSession() {
    // given
    Long contractId = 1L;
    User user = sampleUser().build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    SignatureFile signatureFile =
        new SignatureFile("test.pdf", "application/pdf", "test content".getBytes());
    List<SignatureFile> files = List.of(signatureFile);

    SmartIdSignatureSession signatureSession =
        new SmartIdSignatureSession("session-id", user.getPersonalCode(), List.of());
    signatureSession.setVerificationCode("12345");

    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(contractService.getSignatureFiles(contractId, user)).thenReturn(files);
    when(signService.startSmartIdSign(files, user.getPersonalCode())).thenReturn(signatureSession);

    // when
    MobileSignatureResponse response =
        signatureService.startSmartIdSignature(contractId, authenticatedPerson);

    // then
    assertThat(response.getChallengeCode()).isEqualTo("12345");
    verify(sessionStore).save(signatureSession);
  }

  @Test
  void getSmartIdSignatureStatus_returnsSignatureWhenFileIsSigned() {
    // given
    Long contractId = 1L;
    User user = sampleUser().build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    Member seller = memberFixture().user(user).build();
    CapitalTransferContract contract =
        sampleCapitalTransferContractWithSeller(seller)
            .id(contractId)
            .state(CapitalTransferContractState.CREATED)
            .build();

    SmartIdSignatureSession signatureSession =
        new SmartIdSignatureSession("session-id", user.getPersonalCode(), List.of());
    signatureSession.setVerificationCode("12345");
    byte[] signedFile = "signed content".getBytes();

    when(sessionStore.get(SmartIdSignatureSession.class)).thenReturn(Optional.of(signatureSession));
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(contractService.getContract(contractId, user)).thenReturn(contract);
    when(signService.getSignedFile(signatureSession)).thenReturn(signedFile);

    // when
    MobileSignatureStatusResponse response =
        signatureService.getSmartIdSignatureStatus(contractId, authenticatedPerson);

    // then
    assertThat(response.getStatusCode()).isEqualTo(SignatureStatus.SIGNATURE);
    assertThat(response.getChallengeCode()).isEqualTo("12345");
    verify(contractService).signBySeller(contractId, signedFile, user);
  }

  @Test
  void getSmartIdSignatureStatus_returnsOutstandingTransactionWhenFileNotSigned() {
    // given
    Long contractId = 1L;
    User user = sampleUser().build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    Member seller = memberFixture().user(user).build();
    CapitalTransferContract contract =
        sampleCapitalTransferContractWithSeller(seller)
            .id(contractId)
            .state(CapitalTransferContractState.CREATED)
            .build();

    SmartIdSignatureSession signatureSession =
        new SmartIdSignatureSession("session-id", user.getPersonalCode(), List.of());
    signatureSession.setVerificationCode("12345");

    when(sessionStore.get(SmartIdSignatureSession.class)).thenReturn(Optional.of(signatureSession));
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(contractService.getContract(contractId, user)).thenReturn(contract);
    when(signService.getSignedFile(signatureSession)).thenReturn(null);

    // when
    MobileSignatureStatusResponse response =
        signatureService.getSmartIdSignatureStatus(contractId, authenticatedPerson);

    // then
    assertThat(response.getStatusCode()).isEqualTo(SignatureStatus.OUTSTANDING_TRANSACTION);
    assertThat(response.getChallengeCode()).isEqualTo("12345");
  }

  @Test
  void getSmartIdSignatureStatus_throwsExceptionWhenSessionNotFound() {
    // given
    Long contractId = 1L;
    User user = sampleUser().build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    when(sessionStore.get(SmartIdSignatureSession.class)).thenReturn(Optional.empty());

    // when & then
    assertThatThrownBy(
            () -> signatureService.getSmartIdSignatureStatus(contractId, authenticatedPerson))
        .isInstanceOf(IdSessionException.class);
  }

  @Test
  void getSmartIdSignatureStatus_signsByBuyerWhenSellerAlreadySigned() {
    // given
    Long contractId = 1L;
    User user = sampleUser().build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    User buyerUser = user;
    User sellerUser =
        sampleUser()
            .id(2L)
            .personalCode("37605030298")
            .firstName("Jane")
            .lastName("Smith")
            .email("jane.smith@example.com")
            .build();

    Member buyer = memberFixture().user(buyerUser).build();
    Member seller = memberFixture().user(sellerUser).build();

    CapitalTransferContract contract =
        sampleCapitalTransferContractWithSellerAndBuyer(seller, buyer)
            .id(contractId)
            .state(CapitalTransferContractState.SELLER_SIGNED)
            .build();

    SmartIdSignatureSession signatureSession =
        new SmartIdSignatureSession("session-id", user.getPersonalCode(), List.of());
    signatureSession.setVerificationCode("12345");
    byte[] signedFile = "signed content".getBytes();

    when(sessionStore.get(SmartIdSignatureSession.class)).thenReturn(Optional.of(signatureSession));
    when(userService.getByIdOrThrow(buyerUser.getId())).thenReturn(buyerUser);
    when(contractService.getContract(contractId, user)).thenReturn(contract);
    when(signService.getSignedFile(signatureSession)).thenReturn(signedFile);

    // when
    MobileSignatureStatusResponse response =
        signatureService.getSmartIdSignatureStatus(contractId, authenticatedPerson);

    // then
    assertThat(response.getStatusCode()).isEqualTo(SignatureStatus.SIGNATURE);
    verify(contractService).signByBuyer(contractId, signedFile, user);
  }

  @Test
  void getSmartIdSignatureStatus_throwsExceptionWhenCannotSignInCurrentState() {
    // given
    Long contractId = 1L;
    User user = sampleUser().build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    Member seller = memberFixture().user(user).build();
    CapitalTransferContract contract =
        sampleCapitalTransferContractWithSeller(seller)
            .id(contractId)
            .state(CapitalTransferContractState.BUYER_SIGNED)
            .build();

    SmartIdSignatureSession signatureSession =
        new SmartIdSignatureSession("session-id", user.getPersonalCode(), List.of());
    signatureSession.setVerificationCode("12345");
    byte[] signedFile = "signed content".getBytes();

    when(sessionStore.get(SmartIdSignatureSession.class)).thenReturn(Optional.of(signatureSession));
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(contractService.getContract(contractId, user)).thenReturn(contract);
    when(signService.getSignedFile(signatureSession)).thenReturn(signedFile);

    // when & then
    assertThatThrownBy(
            () -> signatureService.getSmartIdSignatureStatus(contractId, authenticatedPerson))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Cannot sign contract in its current state");
  }

  @Test
  void startIdCardSignature_startsIdCardSignatureSession() {
    // given
    Long contractId = 1L;
    User user = sampleUser().build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    StartIdCardSignCommand command = new StartIdCardSignCommand();
    command.setClientCertificate("test-certificate");

    SignatureFile signatureFile =
        new SignatureFile("test.pdf", "application/pdf", "test content".getBytes());
    List<SignatureFile> files = List.of(signatureFile);

    IdCardSignatureSession signatureSession =
        IdCardSignatureSession.builder().hashToSignInHex("hash-to-sign").build();

    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(contractService.getSignatureFiles(contractId, user)).thenReturn(files);
    when(signService.startIdCardSign(files, "test-certificate")).thenReturn(signatureSession);

    // when
    IdCardSignatureResponse response =
        signatureService.startIdCardSignature(contractId, authenticatedPerson, command);

    // then
    assertThat(response.getHash()).isEqualTo("hash-to-sign");
    verify(sessionStore).save(signatureSession);
  }

  @Test
  void persistIdCardSignedHashAndGetProcessingStatus_returnsSignatureWhenFileIsSigned() {
    // given
    Long contractId = 1L;
    User user = sampleUser().build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    FinishIdCardSignCommand command = new FinishIdCardSignCommand();
    command.setSignedHash("signed-hash");

    Member seller = memberFixture().user(user).build();
    CapitalTransferContract contract =
        sampleCapitalTransferContractWithSeller(seller)
            .id(contractId)
            .state(CapitalTransferContractState.CREATED)
            .build();

    IdCardSignatureSession signatureSession =
        IdCardSignatureSession.builder().hashToSignInHex("hash-to-sign").build();
    byte[] signedFile = "signed content".getBytes();

    when(sessionStore.get(IdCardSignatureSession.class)).thenReturn(Optional.of(signatureSession));
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(contractService.getContract(contractId, user)).thenReturn(contract);
    when(signService.getSignedFile(signatureSession, "signed-hash")).thenReturn(signedFile);

    // when
    IdCardSignatureStatusResponse response =
        signatureService.persistIdCardSignedHashAndGetProcessingStatus(
            contractId, command, authenticatedPerson);

    // then
    assertThat(response.getStatusCode()).isEqualTo(SignatureStatus.SIGNATURE);
    verify(contractService).signBySeller(contractId, signedFile, user);
  }

  @Test
  void
      persistIdCardSignedHashAndGetProcessingStatus_returnsOutstandingTransactionWhenFileNotSigned() {
    // given
    Long contractId = 1L;
    User user = sampleUser().build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    FinishIdCardSignCommand command = new FinishIdCardSignCommand();
    command.setSignedHash("signed-hash");

    Member seller = memberFixture().user(user).build();
    CapitalTransferContract contract =
        sampleCapitalTransferContractWithSeller(seller)
            .id(contractId)
            .state(CapitalTransferContractState.CREATED)
            .build();

    IdCardSignatureSession signatureSession =
        IdCardSignatureSession.builder().hashToSignInHex("hash-to-sign").build();

    when(sessionStore.get(IdCardSignatureSession.class)).thenReturn(Optional.of(signatureSession));
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(contractService.getContract(contractId, user)).thenReturn(contract);
    when(signService.getSignedFile(signatureSession, "signed-hash")).thenReturn(null);

    // when
    IdCardSignatureStatusResponse response =
        signatureService.persistIdCardSignedHashAndGetProcessingStatus(
            contractId, command, authenticatedPerson);

    // then
    assertThat(response.getStatusCode()).isEqualTo(SignatureStatus.OUTSTANDING_TRANSACTION);
  }

  @Test
  void startMobileIdSignature_startsMobileIdSignatureSession() {
    // given
    Long contractId = 1L;
    String phoneNumber = "+37255555555";
    User user = sampleUser().build();
    AuthenticatedPerson authenticatedPerson =
        authenticatedPersonFromUser(user).attributes(Map.of(PHONE_NUMBER, phoneNumber)).build();

    SignatureFile signatureFile =
        new SignatureFile("test.pdf", "application/pdf", "test content".getBytes());
    List<SignatureFile> files = List.of(signatureFile);

    MobileIdSignatureSession signatureSession =
        MobileIdSignatureSession.builder().verificationCode("98765").build();

    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(contractService.getSignatureFiles(contractId, user)).thenReturn(files);
    when(signService.startMobileIdSign(files, user.getPersonalCode(), phoneNumber))
        .thenReturn(signatureSession);

    // when
    MobileSignatureResponse response =
        signatureService.startMobileIdSignature(contractId, authenticatedPerson);

    // then
    assertThat(response.getChallengeCode()).isEqualTo("98765");
    verify(sessionStore).save(signatureSession);
  }

  @Test
  void getMobileIdSignatureStatus_returnsSignatureWhenFileIsSigned() {
    // given
    Long contractId = 1L;
    User user = sampleUser().build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    Member seller = memberFixture().user(user).build();
    CapitalTransferContract contract =
        sampleCapitalTransferContractWithSeller(seller)
            .id(contractId)
            .state(CapitalTransferContractState.CREATED)
            .build();

    MobileIdSignatureSession signatureSession =
        MobileIdSignatureSession.builder().verificationCode("98765").build();
    byte[] signedFile = "signed content".getBytes();

    when(sessionStore.get(MobileIdSignatureSession.class))
        .thenReturn(Optional.of(signatureSession));
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(contractService.getContract(contractId, user)).thenReturn(contract);
    when(signService.getSignedFile(signatureSession)).thenReturn(signedFile);

    // when
    MobileSignatureStatusResponse response =
        signatureService.getMobileIdSignatureStatus(contractId, authenticatedPerson);

    // then
    assertThat(response.getStatusCode()).isEqualTo(SignatureStatus.SIGNATURE);
    assertThat(response.getChallengeCode()).isEqualTo("98765");
    verify(contractService).signBySeller(contractId, signedFile, user);
  }

  @Test
  void getMobileIdSignatureStatus_returnsOutstandingTransactionWhenFileNotSigned() {
    // given
    Long contractId = 1L;
    User user = sampleUser().build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    Member seller = memberFixture().user(user).build();
    CapitalTransferContract contract =
        sampleCapitalTransferContractWithSeller(seller)
            .id(contractId)
            .state(CapitalTransferContractState.CREATED)
            .build();

    MobileIdSignatureSession signatureSession =
        MobileIdSignatureSession.builder().verificationCode("98765").build();

    when(sessionStore.get(MobileIdSignatureSession.class))
        .thenReturn(Optional.of(signatureSession));
    when(userService.getByIdOrThrow(user.getId())).thenReturn(user);
    when(contractService.getContract(contractId, user)).thenReturn(contract);
    when(signService.getSignedFile(signatureSession)).thenReturn(null);

    // when
    MobileSignatureStatusResponse response =
        signatureService.getMobileIdSignatureStatus(contractId, authenticatedPerson);

    // then
    assertThat(response.getStatusCode()).isEqualTo(SignatureStatus.OUTSTANDING_TRANSACTION);
    assertThat(response.getChallengeCode()).isEqualTo("98765");
  }
}
