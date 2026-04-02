package ee.tuleva.onboarding.savings.fund;

import static ee.tuleva.onboarding.party.PartyId.Type.LEGAL_ENTITY;
import static ee.tuleva.onboarding.party.PartyId.Type.PERSON;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.TO_BE_RETURNED;
import static ee.tuleva.onboarding.savings.fund.SavingFundPayment.Status.VERIFIED;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ee.tuleva.onboarding.company.Company;
import ee.tuleva.onboarding.company.CompanyRepository;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.PartyId;
import ee.tuleva.onboarding.party.PartyResolver;
import ee.tuleva.onboarding.payment.event.SavingsPaymentFailedEvent;
import ee.tuleva.onboarding.savings.fund.notification.UnattributedPaymentEvent;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class PaymentVerificationServiceTest {

  SavingFundPaymentRepository savingFundPaymentRepository = mock(SavingFundPaymentRepository.class);
  UserRepository userRepository = mock(UserRepository.class);
  CompanyRepository companyRepository = mock(CompanyRepository.class);
  SavingsFundOnboardingService savingsFundOnboardingService =
      mock(SavingsFundOnboardingService.class);
  SavingsFundLedger savingsFundLedger = mock(SavingsFundLedger.class);
  ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
  PartyResolver partyResolver = new PartyResolver(userRepository, companyRepository);

  PaymentVerificationService service =
      new PaymentVerificationService(
          savingFundPaymentRepository,
          userRepository,
          savingsFundOnboardingService,
          savingsFundLedger,
          applicationEventPublisher,
          new NameMatcher(),
          partyResolver);

  @Test
  void process_noCodeInDescription() {
    var payment = createPayment("37508295796", "my money");

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "selgituses puudub isikukood/registrikood");
    verify(savingsFundLedger)
        .recordUnattributedPayment(payment.getAmount(), payment.getId(), LocalDate.of(2025, 10, 1));
    verify(applicationEventPublisher)
        .publishEvent(
            new UnattributedPaymentEvent(
                payment.getId(), payment.getAmount(), "selgituses puudub isikukood/registrikood"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_personalCodeMismatch() {
    var payment = createPayment("37508295796", "for user 45009144745");

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "selgituses olev isikukood ei klapi maksja isikukoodiga");
    verify(savingsFundLedger)
        .recordUnattributedPayment(payment.getAmount(), payment.getId(), LocalDate.of(2025, 10, 1));
    verify(applicationEventPublisher)
        .publishEvent(
            new UnattributedPaymentEvent(
                payment.getId(),
                payment.getAmount(),
                "selgituses olev isikukood ei klapi maksja isikukoodiga"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_noSuchUser() {
    var payment = createPayment("37508295796", "to user 37508295796");
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.empty());

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "isik ei ole Tuleva klient");
    verify(savingsFundLedger)
        .recordUnattributedPayment(payment.getAmount(), payment.getId(), LocalDate.of(2025, 10, 1));
    verify(applicationEventPublisher)
        .publishEvent(
            new UnattributedPaymentEvent(
                payment.getId(), payment.getAmount(), "isik ei ole Tuleva klient"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_notOnboarded() {
    var payment = createPayment("37508295796", "to user 37508295796");
    var user =
        User.builder().personalCode("37508295796").firstName("PÄRT").lastName("ÕLEKÕRS").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(false);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(new PartyId(PERSON, "37508295796"));
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "see isik ei ole täiendava kogumisfondiga liitunud");
    verify(savingsFundLedger)
        .recordUnattributedPayment(payment.getAmount(), payment.getId(), LocalDate.of(2025, 10, 1));
    verify(applicationEventPublisher)
        .publishEvent(
            new UnattributedPaymentEvent(
                payment.getId(),
                payment.getAmount(),
                "see isik ei ole täiendava kogumisfondiga liitunud"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_userNameMismatch_withoutRemitterIdCode() {
    var payment = createPayment(null, "to user 37508295796");
    var user = User.builder().firstName("PEETER").lastName("MEETER").build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "maksja nimi ei klapi Tuleva andmetega");
    verify(savingsFundLedger)
        .recordUnattributedPayment(payment.getAmount(), payment.getId(), LocalDate.of(2025, 10, 1));
    verify(applicationEventPublisher)
        .publishEvent(
            new UnattributedPaymentEvent(
                payment.getId(), payment.getAmount(), "maksja nimi ei klapi Tuleva andmetega"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_success() {
    var payment = createPayment("37508295796", "to user 37508295796");
    var user =
        User.builder()
            .id(123L)
            .personalCode("37508295796")
            .firstName("PÄRT")
            .lastName("ÕLEKÕRS")
            .build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(new PartyId(PERSON, "37508295796"));
    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(PERSON, user.getPersonalCode()),
            payment.getAmount(),
            payment.getId(),
            LocalDate.of(2025, 10, 1));
    var inOrder = inOrder(savingFundPaymentRepository);
    inOrder
        .verify(savingFundPaymentRepository)
        .attachParty(payment.getId(), new PartyId(PERSON, "37508295796"));
    inOrder.verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_success_noRemitterIdCode() {
    var payment = createPayment(null, "to user 37508295796");
    var user =
        User.builder()
            .id(444L)
            .personalCode("37508295796")
            .firstName("PÄRT")
            .lastName("ÕLEKÕRS")
            .build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(new PartyId(PERSON, "37508295796"));
    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(PERSON, user.getPersonalCode()),
            payment.getAmount(),
            payment.getId(),
            LocalDate.of(2025, 10, 1));
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verify(savingFundPaymentRepository)
        .attachParty(payment.getId(), new PartyId(PERSON, "37508295796"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_success_ignoreOtherValueInRemitterIdCode() {
    var payment = createPayment("P1234", "to user 37508295796");
    var user =
        User.builder()
            .id(444L)
            .personalCode("37508295796")
            .firstName("PÄRT")
            .lastName("ÕLEKÕRS")
            .build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(new PartyId(PERSON, "37508295796"));
    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(PERSON, user.getPersonalCode()),
            payment.getAmount(),
            payment.getId(),
            LocalDate.of(2025, 10, 1));
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verify(savingFundPaymentRepository)
        .attachParty(payment.getId(), new PartyId(PERSON, "37508295796"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_success_allowNameMismatchIfRemitterIdCodeMatches() {
    var payment = createPayment("37508295796", "to user 37508295796");
    var user =
        User.builder()
            .id(123L)
            .personalCode("37508295796")
            .firstName("KEEGI")
            .lastName("TEINE")
            .build();
    when(userRepository.findByPersonalCode(any())).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(new PartyId(PERSON, "37508295796"));
    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(PERSON, user.getPersonalCode()),
            payment.getAmount(),
            payment.getId(),
            LocalDate.of(2025, 10, 1));
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verify(savingFundPaymentRepository)
        .attachParty(payment.getId(), new PartyId(PERSON, "37508295796"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void extractPartyId() {
    assertThat(service.extractPartyId(null)).isEmpty();
    assertThat(service.extractPartyId("")).isEmpty();
    assertThat(service.extractPartyId("abc")).isEmpty();
    assertThat(service.extractPartyId("abc123")).isEmpty();
    assertThat(service.extractPartyId("1234567890")).isEmpty();
    assertThat(service.extractPartyId("12345678901")).isEmpty();
    assertThat(service.extractPartyId("45009144745")).contains(new PartyId(PERSON, "45009144745"));
    assertThat(service.extractPartyId("37508295796")).contains(new PartyId(PERSON, "37508295796"));
    assertThat(service.extractPartyId("37508295795"))
        .withFailMessage("invalid personal code not accepted")
        .isEmpty();
    assertThat(service.extractPartyId("375082957961"))
        .withFailMessage("additional digits not accepted")
        .isEmpty();
    assertThat(service.extractPartyId("137508295796"))
        .withFailMessage("additional digits not accepted")
        .isEmpty();
    assertThat(service.extractPartyId("some prefix 37508295796"))
        .contains(new PartyId(PERSON, "37508295796"));
    assertThat(service.extractPartyId("some prefix,37508295796,some suffix"))
        .contains(new PartyId(PERSON, "37508295796"));
    assertThat(service.extractPartyId("some prefix+37508295796/some suffix,45009144745"))
        .contains(new PartyId(PERSON, "37508295796"));
    assertThat(service.extractPartyId("12345678")).contains(new PartyId(LEGAL_ENTITY, "12345678"));
    assertThat(service.extractPartyId("company 12345678"))
        .contains(new PartyId(LEGAL_ENTITY, "12345678"));
    assertThat(service.extractPartyId("1234567"))
        .withFailMessage("7 digits not a valid registry code")
        .isEmpty();
    assertThat(service.extractPartyId("123456789"))
        .withFailMessage("9 digits not a valid registry code")
        .isEmpty();
  }

  @Test
  void process_companyPayment_success() {
    var payment = createPayment("12345678", "company 12345678");
    var company = Company.builder().registryCode("12345678").name("Tuleva AS").build();
    when(companyRepository.findByRegistryCode("12345678")).thenReturn(Optional.of(company));
    when(savingsFundOnboardingService.isOnboardingCompleted(new PartyId(LEGAL_ENTITY, "12345678")))
        .thenReturn(true);

    service.process(payment);

    verify(companyRepository).findByRegistryCode("12345678");
    verify(savingsFundOnboardingService)
        .isOnboardingCompleted(new PartyId(LEGAL_ENTITY, "12345678"));
    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(LEGAL_ENTITY, "12345678"),
            payment.getAmount(),
            payment.getId(),
            LocalDate.of(2025, 10, 1));
    var inOrder = inOrder(savingFundPaymentRepository);
    inOrder
        .verify(savingFundPaymentRepository)
        .attachParty(payment.getId(), new PartyId(LEGAL_ENTITY, "12345678"));
    inOrder.verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_companyPayment_companyNotFound() {
    var payment = createPayment("12345678", "company 12345678");
    when(companyRepository.findByRegistryCode("12345678")).thenReturn(Optional.empty());

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "ettevõte ei ole Tuleva klient");
  }

  @Test
  void process_companyPayment_notOnboarded() {
    var payment = createPayment("12345678", "company 12345678");
    var company = Company.builder().registryCode("12345678").name("Tuleva AS").build();
    when(companyRepository.findByRegistryCode("12345678")).thenReturn(Optional.of(company));
    when(savingsFundOnboardingService.isOnboardingCompleted(new PartyId(LEGAL_ENTITY, "12345678")))
        .thenReturn(false);

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "see ettevõte ei ole täiendava kogumisfondiga liitunud");
  }

  @Test
  void process_companyPayment_codeMismatch() {
    var payment = createPayment("87654321", "company 12345678");

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(
            payment.getId(), "selgituses olev registrikood ei klapi maksja registrikoodiga");
  }

  @Test
  void process_companyPayment_nameMismatch_noRemitterIdCode() {
    var payment = createPayment(null, "company 12345678");
    var company = Company.builder().registryCode("12345678").name("Tuleva AS").build();
    when(companyRepository.findByRegistryCode("12345678")).thenReturn(Optional.of(company));

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "maksja nimi ei klapi Tuleva andmetega");
  }

  @Test
  void process_companyPayment_success_nameMismatchAllowed_whenRemitterIdCodeMatches() {
    var payment = createPayment("12345678", "company 12345678");
    var company = Company.builder().registryCode("12345678").name("Tuleva AS").build();
    when(companyRepository.findByRegistryCode("12345678")).thenReturn(Optional.of(company));
    when(savingsFundOnboardingService.isOnboardingCompleted(new PartyId(LEGAL_ENTITY, "12345678")))
        .thenReturn(true);

    service.process(payment);

    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(LEGAL_ENTITY, "12345678"),
            payment.getAmount(),
            payment.getId(),
            LocalDate.of(2025, 10, 1));
    verify(savingFundPaymentRepository)
        .attachParty(payment.getId(), new PartyId(LEGAL_ENTITY, "12345678"));
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
  }

  @Test
  void identityCheckFailure_publishesSavingsPaymentFailedEvent_whenPaymentHasParty() {
    var user =
        User.builder()
            .id(123L)
            .personalCode("37508295796")
            .firstName("PÄRT")
            .lastName("ÕLEKÕRS")
            .build();
    var payment =
        SavingFundPayment.builder()
            .id(randomUUID())
            .amount(new BigDecimal("100.00"))
            .partyId(new PartyId(PERSON, user.getPersonalCode()))
            .remitterName("PÄRT ÕLEKÕRS")
            .remitterIban("EE123456789012345678")
            .description("no personal code here")
            .receivedBefore(Instant.parse("2025-10-01T20:59:59.999999Z"))
            .build();

    when(userRepository.findByPersonalCode(user.getPersonalCode())).thenReturn(Optional.of(user));

    service.process(payment);

    verify(applicationEventPublisher)
        .publishEvent(
            argThat(
                (SavingsPaymentFailedEvent e) ->
                    e.getUser().equals(user) && e.getLocale().equals(Locale.of("et"))));
  }

  private SavingFundPayment createPayment(String remitterIdCode, String description) {
    return SavingFundPayment.builder()
        .id(randomUUID())
        .amount(new BigDecimal("100.00"))
        .remitterName("PÄRT ÕLEKÕRS")
        .remitterIban("EE123456789012345678")
        .remitterIdCode(remitterIdCode)
        .description(description)
        .receivedBefore(Instant.parse("2025-10-01T20:59:59.999999Z"))
        .build();
  }
}
