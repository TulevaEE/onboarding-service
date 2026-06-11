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
import ee.tuleva.onboarding.event.TrackableEventType;
import ee.tuleva.onboarding.event.TrackableSystemEvent;
import ee.tuleva.onboarding.ledger.SavingsFundLedger;
import ee.tuleva.onboarding.party.ParentChildLinkService;
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
import java.util.Map;
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
  ParentChildLinkService parentChildLinkService = mock(ParentChildLinkService.class);

  PaymentVerificationService service =
      new PaymentVerificationService(
          savingFundPaymentRepository,
          userRepository,
          savingsFundOnboardingService,
          savingsFundLedger,
          applicationEventPublisher,
          new NameMatcher(),
          partyResolver,
          parentChildLinkService);

  @Test
  void process_returnsPaymentWhenNoCodeAnywhere() {
    var payment = createPayment(null, "my money");

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "makse ei sisalda tuvastatavat isikukoodi/registrikoodi");
    verify(savingsFundLedger)
        .recordUnattributedPayment(payment.getAmount(), payment.getId(), LocalDate.of(2025, 10, 1));
    verify(applicationEventPublisher)
        .publishEvent(
            new UnattributedPaymentEvent(
                payment.getId(),
                payment.getAmount(),
                "makse ei sisalda tuvastatavat isikukoodi/registrikoodi"));
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_success_fallsBackToRemitterIdCodeWhenDescriptionLacksCode() {
    var payment = createPayment("37508295796", "my money");
    var user =
        User.builder()
            .id(123L)
            .personalCode("37508295796")
            .firstName("PÄRT")
            .lastName("ÕLEKÕRS")
            .build();
    when(userRepository.findByPersonalCode("37508295796")).thenReturn(Optional.of(user));
    when(savingsFundOnboardingService.isOnboardingCompleted(any(PartyId.class))).thenReturn(true);

    service.process(payment);

    verify(userRepository).findByPersonalCode("37508295796");
    verify(savingsFundOnboardingService).isOnboardingCompleted(new PartyId(PERSON, "37508295796"));
    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(PERSON, "37508295796"),
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
  void
      process_success_remitterIdCodeWithLetterPrefix_notParsedAsEstonianRegistryCode_wiseScenario() {
    var payment =
        SavingFundPayment.builder()
            .id(randomUUID())
            .amount(new BigDecimal("100.00"))
            .remitterName("PÄRT ÕLEKÕRS")
            .remitterIban("BE72967148007616")
            .remitterIdCode("P13694547")
            .description("37508295796")
            .receivedBefore(Instant.parse("2025-10-01T20:59:59.999999Z"))
            .build();
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
  void extractPartyIdFromDescription() {
    assertThat(service.extractPartyIdFromDescription(null)).isEmpty();
    assertThat(service.extractPartyIdFromDescription("")).isEmpty();
    assertThat(service.extractPartyIdFromDescription("abc")).isEmpty();
    assertThat(service.extractPartyIdFromDescription("abc123")).isEmpty();
    assertThat(service.extractPartyIdFromDescription("1234567890")).isEmpty();
    assertThat(service.extractPartyIdFromDescription("12345678901")).isEmpty();
    assertThat(service.extractPartyIdFromDescription("45009144745"))
        .contains(new PartyId(PERSON, "45009144745"));
    assertThat(service.extractPartyIdFromDescription("37508295796"))
        .contains(new PartyId(PERSON, "37508295796"));
    assertThat(service.extractPartyIdFromDescription("37508295795"))
        .withFailMessage("invalid personal code not accepted")
        .isEmpty();
    assertThat(service.extractPartyIdFromDescription("375082957961"))
        .withFailMessage("additional digits not accepted")
        .isEmpty();
    assertThat(service.extractPartyIdFromDescription("137508295796"))
        .withFailMessage("additional digits not accepted")
        .isEmpty();
    assertThat(service.extractPartyIdFromDescription("some prefix 37508295796"))
        .contains(new PartyId(PERSON, "37508295796"));
    assertThat(service.extractPartyIdFromDescription("some prefix,37508295796,some suffix"))
        .contains(new PartyId(PERSON, "37508295796"));
    assertThat(
            service.extractPartyIdFromDescription(
                "some prefix+37508295796/some suffix,45009144745"))
        .contains(new PartyId(PERSON, "37508295796"));
    assertThat(service.extractPartyIdFromDescription("12345678"))
        .contains(new PartyId(LEGAL_ENTITY, "12345678"));
    assertThat(service.extractPartyIdFromDescription("company 12345678"))
        .contains(new PartyId(LEGAL_ENTITY, "12345678"));
    assertThat(service.extractPartyIdFromDescription("1234567"))
        .withFailMessage("7 digits not a valid registry code")
        .isEmpty();
    assertThat(service.extractPartyIdFromDescription("123456789"))
        .withFailMessage("9 digits not a valid registry code")
        .isEmpty();
    assertThat(service.extractPartyIdFromDescription("P13694547 makse 37508295796"))
        .withFailMessage(
            "11-digit personal code must take precedence over any 8-digit substring elsewhere")
        .contains(new PartyId(PERSON, "37508295796"));
  }

  @Test
  void parsePartyId() {
    assertThat(service.parsePartyId(null)).isEmpty();
    assertThat(service.parsePartyId("")).isEmpty();
    assertThat(service.parsePartyId("37508295796")).contains(new PartyId(PERSON, "37508295796"));
    assertThat(service.parsePartyId("12345678")).contains(new PartyId(LEGAL_ENTITY, "12345678"));
    assertThat(service.parsePartyId("P13694547"))
        .withFailMessage("Wise customer ID must not match as a registry code")
        .isEmpty();
    assertThat(service.parsePartyId("P11268897"))
        .withFailMessage("Wise customer ID must not match as a registry code")
        .isEmpty();
    assertThat(service.parsePartyId("some prefix 37508295796"))
        .withFailMessage("substring extraction must not apply to structured ID field")
        .isEmpty();
    assertThat(service.parsePartyId("37508295796 trailing"))
        .withFailMessage("substring extraction must not apply to structured ID field")
        .isEmpty();
    assertThat(service.parsePartyId("37508295795"))
        .withFailMessage("invalid personal code (bad checksum) must not be accepted")
        .isEmpty();
    assertThat(service.parsePartyId("1234567"))
        .withFailMessage("7 digits not a valid registry code")
        .isEmpty();
    assertThat(service.parsePartyId("123456789"))
        .withFailMessage("9 digits not a valid registry code")
        .isEmpty();
  }

  @Test
  void process_companyPayment_success() {
    var payment = createPayment("14118923", "company 14118923");
    var company = Company.builder().registryCode("14118923").name("Tuleva AS").build();
    when(companyRepository.findByRegistryCode("14118923")).thenReturn(Optional.of(company));
    when(savingsFundOnboardingService.isOnboardingCompleted(new PartyId(LEGAL_ENTITY, "14118923")))
        .thenReturn(true);

    service.process(payment);

    verify(companyRepository).findByRegistryCode("14118923");
    verify(savingsFundOnboardingService)
        .isOnboardingCompleted(new PartyId(LEGAL_ENTITY, "14118923"));
    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(LEGAL_ENTITY, "14118923"),
            payment.getAmount(),
            payment.getId(),
            LocalDate.of(2025, 10, 1));
    var inOrder = inOrder(savingFundPaymentRepository);
    inOrder
        .verify(savingFundPaymentRepository)
        .attachParty(payment.getId(), new PartyId(LEGAL_ENTITY, "14118923"));
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
  void
      process_success_companyPayment_remitterIdCodeWithLetterPrefix_notParsedAsEstonianRegistryCode() {
    var payment =
        SavingFundPayment.builder()
            .id(randomUUID())
            .amount(new BigDecimal("100.00"))
            .remitterName("Tuleva AS")
            .remitterIban("BE72967148007616")
            .remitterIdCode("P13694547")
            .description("14118923")
            .receivedBefore(Instant.parse("2025-10-01T20:59:59.999999Z"))
            .build();
    var company = Company.builder().registryCode("14118923").name("Tuleva AS").build();
    when(companyRepository.findByRegistryCode("14118923")).thenReturn(Optional.of(company));
    when(savingsFundOnboardingService.isOnboardingCompleted(new PartyId(LEGAL_ENTITY, "14118923")))
        .thenReturn(true);

    service.process(payment);

    verify(companyRepository).findByRegistryCode("14118923");
    verify(savingsFundOnboardingService)
        .isOnboardingCompleted(new PartyId(LEGAL_ENTITY, "14118923"));
    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(LEGAL_ENTITY, "14118923"),
            payment.getAmount(),
            payment.getId(),
            LocalDate.of(2025, 10, 1));
    verify(savingFundPaymentRepository)
        .attachParty(payment.getId(), new PartyId(LEGAL_ENTITY, "14118923"));
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_companyPayment_success_nameMismatchAllowed_whenRemitterIdCodeMatches() {
    var payment = createPayment("14118923", "company 14118923");
    var company = Company.builder().registryCode("14118923").name("Tuleva AS").build();
    when(companyRepository.findByRegistryCode("14118923")).thenReturn(Optional.of(company));
    when(savingsFundOnboardingService.isOnboardingCompleted(new PartyId(LEGAL_ENTITY, "14118923")))
        .thenReturn(true);

    service.process(payment);

    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(LEGAL_ENTITY, "14118923"),
            payment.getAmount(),
            payment.getId(),
            LocalDate.of(2025, 10, 1));
    verify(savingFundPaymentRepository)
        .attachParty(payment.getId(), new PartyId(LEGAL_ENTITY, "14118923"));
    verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
  }

  @Test
  void process_companyPayment_preGoLive_returnsPaymentForNonTulevaFondidLegalEntity() {
    var payment = createPayment("12345678", "company 12345678");
    var company = Company.builder().registryCode("12345678").name("Tuleva AS").build();
    when(companyRepository.findByRegistryCode("12345678")).thenReturn(Optional.of(company));
    when(savingsFundOnboardingService.isOnboardingCompleted(new PartyId(LEGAL_ENTITY, "12345678")))
        .thenReturn(true);

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(
            payment.getId(), "pre-go-live: only Tuleva Fondid AS can receive TKF payments");
    verify(savingsFundLedger)
        .recordUnattributedPayment(payment.getAmount(), payment.getId(), LocalDate.of(2025, 10, 1));
    verify(applicationEventPublisher)
        .publishEvent(
            new UnattributedPaymentEvent(
                payment.getId(),
                payment.getAmount(),
                "pre-go-live: only Tuleva Fondid AS can receive TKF payments"));
    verify(savingsFundLedger, never()).recordPaymentReceived(any(), any(), any(), any());
    verify(savingFundPaymentRepository, never()).attachParty(any(), any());
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_companyPayment_preGoLive_tulevaFondidAsPaymentGoesThrough() {
    var payment = createPayment("14118923", "company 14118923");
    var company = Company.builder().registryCode("14118923").name("PÄRT ÕLEKÕRS").build();
    when(companyRepository.findByRegistryCode("14118923")).thenReturn(Optional.of(company));
    when(savingsFundOnboardingService.isOnboardingCompleted(new PartyId(LEGAL_ENTITY, "14118923")))
        .thenReturn(true);

    service.process(payment);

    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(LEGAL_ENTITY, "14118923"),
            payment.getAmount(),
            payment.getId(),
            LocalDate.of(2025, 10, 1));
    var inOrder = inOrder(savingFundPaymentRepository);
    inOrder
        .verify(savingFundPaymentRepository)
        .attachParty(payment.getId(), new PartyId(LEGAL_ENTITY, "14118923"));
    inOrder.verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verifyNoMoreInteractions(savingFundPaymentRepository);
  }

  @Test
  void process_personalPayment_preGoLive_personPaymentsUnaffected() {
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

    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(PERSON, "37508295796"),
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

  @Test
  void process_parentRepresentingChild_activeLink_paymentVerifiedForChild() {
    var parentCode = "38812121215";
    var childCode = "61506150006";
    var payment = createPayment(parentCode, "for child " + childCode);
    var child =
        User.builder()
            .id(456L)
            .personalCode(childCode)
            .firstName("MARI")
            .lastName("MAASIKAS")
            .build();
    when(userRepository.findByPersonalCode(childCode)).thenReturn(Optional.of(child));
    when(savingsFundOnboardingService.isOnboardingCompleted(new PartyId(PERSON, childCode)))
        .thenReturn(true);
    when(parentChildLinkService.represents(parentCode, childCode)).thenReturn(true);

    service.process(payment);

    verify(savingsFundLedger)
        .recordPaymentReceived(
            new PartyId(PERSON, childCode),
            payment.getAmount(),
            payment.getId(),
            LocalDate.of(2025, 10, 1));
    var inOrder = inOrder(savingFundPaymentRepository);
    inOrder
        .verify(savingFundPaymentRepository)
        .attachParty(payment.getId(), new PartyId(PERSON, childCode));
    inOrder.verify(savingFundPaymentRepository).changeStatus(payment.getId(), VERIFIED);
    verifyNoMoreInteractions(savingFundPaymentRepository);
    verify(applicationEventPublisher)
        .publishEvent(
            new TrackableSystemEvent(
                TrackableEventType.MINOR_DEPOSIT_VERIFIED,
                Map.of(
                    "parentPersonalCode",
                    parentCode,
                    "childPersonalCode",
                    childCode,
                    "paymentId",
                    payment.getId(),
                    "amount",
                    payment.getAmount())));
  }

  @Test
  void process_parentRepresentingChild_noActiveLink_bouncesAsCodeMismatch() {
    var parentCode = "38812121215";
    var childCode = "61506150006";
    var payment = createPayment(parentCode, "for child " + childCode);
    when(parentChildLinkService.represents(parentCode, childCode)).thenReturn(false);

    service.process(payment);

    verify(savingFundPaymentRepository).changeStatus(payment.getId(), TO_BE_RETURNED);
    verify(savingFundPaymentRepository)
        .addReturnReason(payment.getId(), "selgituses olev isikukood ei klapi maksja isikukoodiga");
    verify(savingFundPaymentRepository, never()).attachParty(any(), any());
    verifyNoMoreInteractions(savingFundPaymentRepository);
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
