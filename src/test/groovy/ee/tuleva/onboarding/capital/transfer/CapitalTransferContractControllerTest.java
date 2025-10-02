package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.authenticatedPersonFromUser;
import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonNonMember;
import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.CAPITAL_PAYMENT;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractFixture.sampleCapitalTransferContract;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.PAYMENT_CONFIRMED_BY_BUYER;
import static ee.tuleva.onboarding.capital.transfer.CapitalTransferContractState.PAYMENT_CONFIRMED_BY_SELLER;
import static ee.tuleva.onboarding.config.SecurityTestHelper.mockAuthentication;
import static ee.tuleva.onboarding.user.MemberFixture.memberFixture;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.capital.transfer.CapitalTransferContract.CapitalTransferAmount;
import ee.tuleva.onboarding.mandate.command.FinishIdCardSignCommand;
import ee.tuleva.onboarding.mandate.command.StartIdCardSignCommand;
import ee.tuleva.onboarding.signature.response.IdCardSignatureResponse;
import ee.tuleva.onboarding.signature.response.IdCardSignatureStatusResponse;
import ee.tuleva.onboarding.signature.response.MobileSignatureResponse;
import ee.tuleva.onboarding.signature.response.MobileSignatureStatusResponse;
import ee.tuleva.onboarding.signature.response.SignatureStatus;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import ee.tuleva.onboarding.user.member.Member;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CapitalTransferContractController.class)
@WithMockUser
class CapitalTransferContractControllerTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CapitalTransferContractService contractService;
  @Autowired private CapitalTransferSignatureService signatureService;
  @Autowired private UserService userService;

  @TestConfiguration
  static class CapitalTransferContractControllerTestConfiguration {
    @Bean
    @Primary
    public CapitalTransferContractService contractService() {
      return Mockito.mock(CapitalTransferContractService.class);
    }

    @Bean
    @Primary
    public CapitalTransferSignatureService signatureService() {
      return Mockito.mock(CapitalTransferSignatureService.class);
    }

    @Bean
    @Primary
    public UserService userService() {
      return Mockito.mock(UserService.class);
    }
  }

  @BeforeEach
  void setUp() {
    Mockito.reset(contractService, signatureService);
  }

  @Test
  void createContract_creates_contract_successfully() throws Exception {
    // given
    CreateCapitalTransferContractCommand command =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(1L)
            .iban("EE471000001020145685")
            .transferAmounts(
                List.of(
                    new CapitalTransferAmount(
                        CAPITAL_PAYMENT,
                        new BigDecimal("1250.0"),
                        new BigDecimal("100.0"),
                        new BigDecimal("1.0"))))
            .build();

    User sellerUser = sampleUser().id(1L).personalCode("37605030299").build();
    Member sellerMember = memberFixture().id(1L).user(sellerUser).memberNumber(1001).build();
    User buyerUser = sampleUser().id(2L).personalCode("60001019906").build();
    Member buyerMember = memberFixture().id(2L).user(buyerUser).memberNumber(1002).build();

    CapitalTransferContract contract =
        sampleCapitalTransferContract()
            .id(1L)
            .seller(sellerMember)
            .buyer(buyerMember)
            .state(CapitalTransferContractState.CREATED)
            .build();

    AuthenticatedPerson seller = authenticatedPersonFromUser(sellerUser).build();

    Authentication authentication =
        new UsernamePasswordAuthenticationToken(seller, null, Collections.emptyList());

    given(
            contractService.create(
                Mockito.any(AuthenticatedPerson.class),
                Mockito.any(CreateCapitalTransferContractCommand.class)))
        .willReturn(contract);

    // when, then
    mvc.perform(
            post("/v1/capital-transfer-contracts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command))
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.state", is("CREATED")));

    verify(contractService)
        .create(
            Mockito.any(AuthenticatedPerson.class),
            Mockito.any(CreateCapitalTransferContractCommand.class));
  }

  @Test
  void getContract_returns_contract_by_id() throws Exception {
    // given
    Long contractId = 1L;
    User sellerUser = sampleUser().id(1L).personalCode("37605030299").build();
    Member sellerMember = memberFixture().id(1L).user(sellerUser).memberNumber(1001).build();
    User buyerUser = sampleUser().id(2L).personalCode("60001019906").build();
    Member buyerMember = memberFixture().id(2L).user(buyerUser).memberNumber(1002).build();

    CapitalTransferContract contract =
        sampleCapitalTransferContract()
            .id(contractId)
            .seller(sellerMember)
            .buyer(buyerMember)
            .state(CapitalTransferContractState.CREATED)
            .build();

    when(userService.getByIdOrThrow(sampleAuthenticatedPersonNonMember().build().getUserId()))
        .thenReturn(sellerUser);
    given(contractService.getContract(contractId, sellerUser)).willReturn(contract);

    // when, then
    mvc.perform(
            get("/v1/capital-transfer-contracts/{id}", contractId)
                .with(authentication(mockAuthentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.state", is("CREATED")));

    verify(contractService).getContract(contractId, sellerUser);
  }

  @Test
  void getContracts_returns_my_contracts() throws Exception {
    // given
    Long contractId = 1L;
    User sellerUser = sampleUser().id(1L).personalCode("37605030299").build();
    Member sellerMember = memberFixture().id(1L).user(sellerUser).memberNumber(1001).build();
    User buyerUser = sampleUser().id(2L).personalCode("60001019906").build();
    Member buyerMember = memberFixture().id(2L).user(buyerUser).memberNumber(1002).build();

    CapitalTransferContract contract =
        sampleCapitalTransferContract()
            .id(contractId)
            .seller(sellerMember)
            .buyer(buyerMember)
            .state(CapitalTransferContractState.CREATED)
            .build();

    when(userService.getByIdOrThrow(sampleAuthenticatedPersonNonMember().build().getUserId()))
        .thenReturn(sellerUser);
    given(contractService.getMyContracts(sellerUser)).willReturn(List.of(contract));

    // when, then
    mvc.perform(get("/v1/capital-transfer-contracts").with(authentication(mockAuthentication())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id", is(1)))
        .andExpect(jsonPath("$[0].state", is("CREATED")));

    verify(contractService).getMyContracts(sellerUser);
  }

  @Test
  void updateContractState_confirms_payment_by_buyer() throws Exception {
    // given
    Long contractId = 1L;
    UpdateCapitalTransferContractStateCommand command =
        new UpdateCapitalTransferContractStateCommand();
    command.setState(PAYMENT_CONFIRMED_BY_BUYER);

    User sellerUser = sampleUser().id(1L).personalCode("37605030299").build();
    Member sellerMember = memberFixture().id(1L).user(sellerUser).memberNumber(1001).build();
    User buyerUser = sampleUser().id(2L).personalCode("60001019906").build();
    Member buyerMember = memberFixture().id(2L).user(buyerUser).memberNumber(1002).build();

    CapitalTransferContract contract =
        sampleCapitalTransferContract()
            .id(contractId)
            .seller(sellerMember)
            .buyer(buyerMember)
            .state(PAYMENT_CONFIRMED_BY_BUYER)
            .build();

    when(userService.getByIdOrThrow(sampleAuthenticatedPersonNonMember().build().getUserId()))
        .thenReturn(buyerUser);
    given(contractService.updateStateByUser(contractId, PAYMENT_CONFIRMED_BY_BUYER, buyerUser))
        .willReturn(contract);

    // when, then
    mvc.perform(
            patch("/v1/capital-transfer-contracts/{id}", contractId)
                .with(authentication(mockAuthentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.state", is("PAYMENT_CONFIRMED_BY_BUYER")));

    verify(contractService).updateStateByUser(contractId, PAYMENT_CONFIRMED_BY_BUYER, buyerUser);
  }

  @Test
  void updateContractState_confirms_payment_by_seller() throws Exception {
    // given
    Long contractId = 1L;
    UpdateCapitalTransferContractStateCommand command =
        new UpdateCapitalTransferContractStateCommand();
    command.setState(CapitalTransferContractState.PAYMENT_CONFIRMED_BY_SELLER);

    User sellerUser = sampleUser().id(1L).personalCode("37605030299").build();
    Member sellerMember = memberFixture().id(1L).user(sellerUser).memberNumber(1001).build();
    User buyerUser = sampleUser().id(2L).personalCode("60001019906").build();
    Member buyerMember = memberFixture().id(2L).user(buyerUser).memberNumber(1002).build();

    CapitalTransferContract contract =
        sampleCapitalTransferContract()
            .id(contractId)
            .seller(sellerMember)
            .buyer(buyerMember)
            .state(CapitalTransferContractState.PAYMENT_CONFIRMED_BY_SELLER)
            .build();

    when(userService.getByIdOrThrow(sampleAuthenticatedPersonNonMember().build().getUserId()))
        .thenReturn(sellerUser);
    given(contractService.updateStateByUser(contractId, PAYMENT_CONFIRMED_BY_SELLER, sellerUser))
        .willReturn(contract);

    // when, then
    mvc.perform(
            patch("/v1/capital-transfer-contracts/{id}", contractId)
                .with(authentication(mockAuthentication()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id", is(1)))
        .andExpect(jsonPath("$.state", is("PAYMENT_CONFIRMED_BY_SELLER")));

    verify(contractService).updateStateByUser(contractId, PAYMENT_CONFIRMED_BY_SELLER, sellerUser);
  }

  @Test
  void startSmartIdSignature_initiates_signature_process() throws Exception {
    // given
    Long contractId = 1L;

    User user = sampleUser().id(1L).personalCode("37605030299").build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    Authentication authentication =
        new UsernamePasswordAuthenticationToken(authenticatedPerson, null, Collections.emptyList());

    MobileSignatureResponse response = new MobileSignatureResponse("1234");

    given(
            signatureService.startSmartIdSignature(
                Mockito.eq(contractId), Mockito.any(AuthenticatedPerson.class)))
        .willReturn(response);

    // when, then
    mvc.perform(
            put("/v1/capital-transfer-contracts/{id}/signature/smart-id", contractId)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.challengeCode", is("1234")));

    verify(signatureService)
        .startSmartIdSignature(Mockito.eq(contractId), Mockito.any(AuthenticatedPerson.class));
  }

  @Test
  void getSmartIdSignatureStatus_returns_signature_status() throws Exception {
    // given
    Long contractId = 1L;
    User user = sampleUser().id(1L).personalCode("37605030299").build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    Authentication authentication =
        new UsernamePasswordAuthenticationToken(authenticatedPerson, null, Collections.emptyList());

    MobileSignatureStatusResponse response =
        new MobileSignatureStatusResponse(SignatureStatus.SIGNATURE, "1234");

    given(
            signatureService.getSmartIdSignatureStatus(
                Mockito.eq(contractId), Mockito.any(AuthenticatedPerson.class)))
        .willReturn(response);

    // when, then
    mvc.perform(
            get("/v1/capital-transfer-contracts/{id}/signature/smart-id/status", contractId)
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statusCode", is("SIGNATURE")))
        .andExpect(jsonPath("$.challengeCode", is("1234")));

    verify(signatureService)
        .getSmartIdSignatureStatus(Mockito.eq(contractId), Mockito.any(AuthenticatedPerson.class));
  }

  @Test
  void startIdCardSignature_initiates_id_card_signature_process() throws Exception {
    // given
    Long contractId = 1L;
    User user = sampleUser().id(1L).personalCode("37605030299").build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    Authentication authentication =
        new UsernamePasswordAuthenticationToken(authenticatedPerson, null, Collections.emptyList());

    StartIdCardSignCommand command = new StartIdCardSignCommand();
    command.setClientCertificate("test-certificate");

    IdCardSignatureResponse response = new IdCardSignatureResponse("hash-to-sign");

    given(
            signatureService.startIdCardSignature(
                Mockito.eq(contractId),
                Mockito.any(AuthenticatedPerson.class),
                Mockito.any(StartIdCardSignCommand.class)))
        .willReturn(response);

    // when, then
    mvc.perform(
            put("/v1/capital-transfer-contracts/{id}/signature/id-card", contractId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command))
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hash", is("hash-to-sign")));

    verify(signatureService)
        .startIdCardSignature(
            Mockito.eq(contractId),
            Mockito.any(AuthenticatedPerson.class),
            Mockito.any(StartIdCardSignCommand.class));
  }

  @Test
  void persistIdCardSignedHashOrGetSignatureStatus_returns_signature_status() throws Exception {
    // given
    Long contractId = 1L;
    User user = sampleUser().id(1L).personalCode("37605030299").build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    Authentication authentication =
        new UsernamePasswordAuthenticationToken(authenticatedPerson, null, Collections.emptyList());

    FinishIdCardSignCommand command = new FinishIdCardSignCommand();
    command.setSignedHash("signed-hash");

    IdCardSignatureStatusResponse response =
        new IdCardSignatureStatusResponse(SignatureStatus.SIGNATURE);

    given(
            signatureService.persistIdCardSignedHashAndGetProcessingStatus(
                Mockito.eq(contractId),
                Mockito.any(FinishIdCardSignCommand.class),
                Mockito.any(AuthenticatedPerson.class)))
        .willReturn(response);

    // when, then
    mvc.perform(
            put("/v1/capital-transfer-contracts/{id}/signature/id-card/status", contractId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(command))
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statusCode", is("SIGNATURE")));

    verify(signatureService)
        .persistIdCardSignedHashAndGetProcessingStatus(
            Mockito.eq(contractId),
            Mockito.any(FinishIdCardSignCommand.class),
            Mockito.any(AuthenticatedPerson.class));
  }

  @Test
  void startMobileIdSignature_initiates_mobile_id_signature_process() throws Exception {
    // given
    Long contractId = 1L;
    User user = sampleUser().id(1L).personalCode("37605030299").build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    Authentication authentication =
        new UsernamePasswordAuthenticationToken(authenticatedPerson, null, Collections.emptyList());

    MobileSignatureResponse response = new MobileSignatureResponse("5678");

    given(
            signatureService.startMobileIdSignature(
                Mockito.eq(contractId), Mockito.any(AuthenticatedPerson.class)))
        .willReturn(response);

    // when, then
    mvc.perform(
            put("/v1/capital-transfer-contracts/{id}/signature/mobile-id", contractId)
                .with(csrf())
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.challengeCode", is("5678")));

    verify(signatureService)
        .startMobileIdSignature(Mockito.eq(contractId), Mockito.any(AuthenticatedPerson.class));
  }

  @Test
  void getMobileIdSignatureStatus_returns_mobile_id_signature_status() throws Exception {
    // given
    Long contractId = 1L;
    User user = sampleUser().id(1L).personalCode("37605030299").build();
    AuthenticatedPerson authenticatedPerson = authenticatedPersonFromUser(user).build();

    Authentication authentication =
        new UsernamePasswordAuthenticationToken(authenticatedPerson, null, Collections.emptyList());

    MobileSignatureStatusResponse response =
        new MobileSignatureStatusResponse(SignatureStatus.SIGNATURE, "5678");

    given(
            signatureService.getMobileIdSignatureStatus(
                Mockito.eq(contractId), Mockito.any(AuthenticatedPerson.class)))
        .willReturn(response);

    // when, then
    mvc.perform(
            get("/v1/capital-transfer-contracts/{id}/signature/mobile-id/status", contractId)
                .with(authentication(authentication)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statusCode", is("SIGNATURE")))
        .andExpect(jsonPath("$.challengeCode", is("5678")));

    verify(signatureService)
        .getMobileIdSignatureStatus(Mockito.eq(contractId), Mockito.any(AuthenticatedPerson.class));
  }
}
