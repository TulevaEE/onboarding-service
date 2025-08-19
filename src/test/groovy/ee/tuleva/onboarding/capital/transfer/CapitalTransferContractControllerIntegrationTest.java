package ee.tuleva.onboarding.capital.transfer;

import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.CAPITAL_PAYMENT;
import static ee.tuleva.onboarding.capital.event.member.MemberCapitalEventType.MEMBERSHIP_BONUS;
import static ee.tuleva.onboarding.time.TestClockHolder.clock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.auth.authority.Authority;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.auth.session.GenericSessionStore;
import ee.tuleva.onboarding.capital.ApiCapitalEvent;
import ee.tuleva.onboarding.capital.CapitalService;
import ee.tuleva.onboarding.currency.Currency;
import ee.tuleva.onboarding.mandate.email.persistence.Email;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.signature.SignatureFile;
import ee.tuleva.onboarding.signature.SignatureService;
import ee.tuleva.onboarding.signature.smartid.SmartIdSignatureSession;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import ee.tuleva.onboarding.user.member.Member;
import ee.tuleva.onboarding.user.member.MemberRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CapitalTransferContractControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private MemberRepository memberRepository;

  @Autowired private SignatureService signatureService;
  @Autowired private GenericSessionStore sessionStore;
  @Autowired private EmailService emailService;
  @Autowired private CapitalService capitalService;
  @Autowired private EmailPersistenceService emailPersistenceService;

  private User sellerUser;
  private Member sellerMember;
  private User buyerUser;
  private Member buyerMember;

  private Authentication sellerAuthentication;
  private Authentication buyerAuthentication;

  @TestConfiguration
  static class TestConfig {
    @Bean
    @Primary
    public SignatureService signatureService() {
      return mock(SignatureService.class);
    }

    @Bean
    @Primary
    public GenericSessionStore genericSessionStore() {
      return mock(GenericSessionStore.class);
    }

    @Bean
    @Primary
    public EmailService emailService() {
      return mock(EmailService.class);
    }

    @Bean
    @Primary
    public CapitalService capitalService() {
      return mock(CapitalService.class);
    }

    @Bean
    @Primary
    public EmailPersistenceService emailPersistenceService() {
      return mock(EmailPersistenceService.class);
    }
  }

  @BeforeEach
  void setUp() {
    // given
    sellerUser =
        userRepository.save(
            User.builder()
                .personalCode("37605030299")
                .firstName("Mikk")
                .lastName("Seller")
                .email("seller@tuleva.ee")
                .build());
    sellerMember =
        memberRepository.save(
            Member.builder().user(sellerUser).memberNumber(1001).active(true).build());
    sellerUser.setMember(sellerMember);
    sellerUser = userRepository.save(sellerUser);

    AuthenticatedPerson sellerAuth =
        AuthenticatedPerson.builder()
            .userId(sellerUser.getId())
            .personalCode(sellerUser.getPersonalCode())
            .firstName(sellerUser.getFirstName())
            .lastName(sellerUser.getLastName())
            .build();

    var authorities =
        new ArrayList<>(
            List.of(
                new SimpleGrantedAuthority(Authority.USER),
                new SimpleGrantedAuthority(Authority.MEMBER)));
    sellerAuthentication = new UsernamePasswordAuthenticationToken(sellerAuth, null, authorities);

    buyerUser =
        userRepository.save(
            User.builder()
                .personalCode("60001019906")
                .firstName("Mari")
                .lastName("Buyer")
                .email("buyer@tuleva.ee")
                .build());
    buyerMember =
        memberRepository.save(
            Member.builder().user(buyerUser).memberNumber(1002).active(true).build());
    buyerUser.setMember(buyerMember);
    buyerUser = userRepository.save(buyerUser);

    AuthenticatedPerson buyerAuth =
        AuthenticatedPerson.builder()
            .userId(buyerUser.getId())
            .personalCode(buyerUser.getPersonalCode())
            .firstName(buyerUser.getFirstName())
            .lastName(buyerUser.getLastName())
            .build();
    buyerAuthentication = new UsernamePasswordAuthenticationToken(buyerAuth, null, authorities);

    when(emailService.newMandrillMessage(any(), any(), any(), any(), any()))
        .thenReturn(new MandrillMessage());

    when(emailPersistenceService.save(any(), any(), any(), any()))
        .thenReturn(Email.builder().id(1L).build());

    when(capitalService.getCapitalEvents(sellerMember.getId()))
        .thenReturn(
            List.of(
                new ApiCapitalEvent(
                    LocalDate.now(clock), CAPITAL_PAYMENT, BigDecimal.valueOf(1000), Currency.EUR),
                new ApiCapitalEvent(
                    LocalDate.now(clock), MEMBERSHIP_BONUS, BigDecimal.valueOf(5), Currency.EUR)));

    when(capitalService.getCapitalConcentrationUnitLimit()).thenReturn(BigDecimal.valueOf(1e7));
  }

  @Test
  void full_capital_transfer_flow() throws Exception {
    // when
    CreateCapitalTransferContractCommand createCommand =
        CreateCapitalTransferContractCommand.builder()
            .buyerMemberId(buyerMember.getId())
            .iban("EE471000001020145685")
            .totalPrice(new BigDecimal("1250.0"))
            .unitCount(new BigDecimal("100.0"))
            .unitsOfMemberBonus(new BigDecimal("2.0"))
            .build();

    var sellerMemberId = sellerMember.getId();

    String responseBody =
        mockMvc
            .perform(
                post("/v1/capital-transfer-contracts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createCommand))
                    .with(authentication(sellerAuthentication)))
            // then
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.seller.firstName").value("Mikk"))
            .andExpect(jsonPath("$.buyer.firstName").value("Mari"))
            .andExpect(jsonPath("$.state").value("CREATED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Long contractId = objectMapper.readTree(responseBody).get("id").asLong();

    // given
    List<SignatureFile> files =
        List.of(
            new SignatureFile(
                "contract.pdf", "application/pdf", "Contract content placeholder".getBytes()));
    SmartIdSignatureSession sellerSession =
        new SmartIdSignatureSession("seller-session-id", "37605030299", files);
    sellerSession.setVerificationCode("verification1");

    when(sessionStore.get(SmartIdSignatureSession.class)).thenReturn(Optional.of(sellerSession));
    when(signatureService.startSmartIdSign(any(), eq("37605030299"))).thenReturn(sellerSession);

    // when
    mockMvc
        .perform(
            put("/v1/capital-transfer-contracts/{id}/signature/smart-id", contractId)
                .with(authentication(sellerAuthentication)))
        // then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.challengeCode").value("verification1"));

    // given
    when(signatureService.getSignedFile(sellerSession))
        .thenReturn("signed content by seller".getBytes());

    when(emailService.send(
            any(User.class), any(MandrillMessage.class), eq("capital_transfer_seller_signed_et")))
        .thenReturn(Optional.of(new MandrillMessageStatus()));
    when(emailService.send(
            any(User.class), any(MandrillMessage.class), eq("capital_transfer_buyer_to_sign_et")))
        .thenReturn(Optional.of(new MandrillMessageStatus()));

    // when
    mockMvc
        .perform(
            get("/v1/capital-transfer-contracts/{id}/signature/smart-id/status", contractId)
                .with(authentication(sellerAuthentication)))
        // then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statusCode").value("SIGNATURE"));

    // then

    // given
    SmartIdSignatureSession buyerSession =
        new SmartIdSignatureSession("buyer-session-id", "60001019906", files);
    buyerSession.setVerificationCode("verification2");

    when(sessionStore.get(SmartIdSignatureSession.class)).thenReturn(Optional.of(buyerSession));
    when(signatureService.startSmartIdSign(any(), eq("60001019906"))).thenReturn(buyerSession);
    when(signatureService.getSignedFile(buyerSession))
        .thenReturn("signed content by buyer".getBytes());

    // when
    mockMvc
        .perform(
            put("/v1/capital-transfer-contracts/{id}/signature/smart-id", contractId)
                .with(authentication(buyerAuthentication)))
        // then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.challengeCode").value("verification2"));

    // when
    mockMvc
        .perform(
            get("/v1/capital-transfer-contracts/{id}/signature/smart-id/status", contractId)
                .with(authentication(buyerAuthentication)))
        // then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.statusCode").value("SIGNATURE"));

    // given
    UpdateCapitalTransferContractStateCommand confirmPaymentCommand =
        new UpdateCapitalTransferContractStateCommand();
    confirmPaymentCommand.setState(CapitalTransferContractState.PAYMENT_CONFIRMED_BY_BUYER);

    when(emailService.send(
            any(User.class),
            any(MandrillMessage.class),
            eq("capital_transfer_confirmed_by_buyer_et")))
        .thenReturn(Optional.of(new MandrillMessageStatus()));

    // when
    mockMvc
        .perform(
            patch("/v1/capital-transfer-contracts/{id}", contractId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmPaymentCommand))
                .with(authentication(buyerAuthentication)))
        // then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("PAYMENT_CONFIRMED_BY_BUYER"));

    // given
    UpdateCapitalTransferContractStateCommand confirmReceivedCommand =
        new UpdateCapitalTransferContractStateCommand();
    confirmReceivedCommand.setState(CapitalTransferContractState.PAYMENT_CONFIRMED_BY_SELLER);

    // when
    mockMvc
        .perform(
            patch("/v1/capital-transfer-contracts/{id}", contractId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmReceivedCommand))
                .with(authentication(sellerAuthentication)))
        // then
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("PAYMENT_CONFIRMED_BY_SELLER"));
  }
}
