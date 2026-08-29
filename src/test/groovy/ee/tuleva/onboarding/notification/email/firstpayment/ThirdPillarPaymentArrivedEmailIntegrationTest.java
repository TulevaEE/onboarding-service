package ee.tuleva.onboarding.notification.email.firstpayment;

import static ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionFixture.exampleTransactionBuilder;
import static ee.tuleva.onboarding.notification.email.EmailType.THIRD_PILLAR_PAYMENT_ARRIVED;
import static ee.tuleva.onboarding.notification.email.EmailType.THIRD_PILLAR_PAYMENT_REMINDER_MANDATE;
import static ee.tuleva.onboarding.notification.email.EmailType.THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.analytics.transaction.thirdpillar.AnalyticsThirdPillarTransactionRepository;
import ee.tuleva.onboarding.analytics.transaction.thirdpillar.FirstThirdPillarPayment;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwner;
import ee.tuleva.onboarding.analytics.transaction.unitowner.UnitOwnerRepository;
import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.EmailStatus;
import ee.tuleva.onboarding.notification.email.persistence.EmailRepository;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(
    properties = {
      "third-pillar-payment-arrived.dry-run=false",
      "third-pillar-payment-arrived.history-floor=9999-12-31"
    })
class ThirdPillarPaymentArrivedEmailIntegrationTest {

  private static final String OWN_MONEY_SOURCE = "Osakute väljalase isikult laekumiste alusel";
  private static final String EMPLOYER_SOURCE = "Osakute väljalase tööandjalt laekumiste alusel";

  private static final String ACCOUNT_HOLDER = TestPersonalCodes.withValidChecksum("3860101000");
  private static final String IN_APP_PAYER = TestPersonalCodes.withValidChecksum("3850101000");
  private static final String LONG_TIME_SAVER = TestPersonalCodes.withValidChecksum("3881212121");
  private static final String EMPLOYER_PAID = TestPersonalCodes.withValidChecksum("3900101000");
  private static final String REGISTRY_ONLY = TestPersonalCodes.withValidChecksum("3870101000");
  private static final String PENSIONER = TestPersonalCodes.withValidChecksum("3550101000");
  private static final String UNDERAGE = TestPersonalCodes.withValidChecksum("5160101000");
  private static final String DECEASED = TestPersonalCodes.withValidChecksum("3840101000");
  private static final String SECOND_PILLAR_LEAVER =
      TestPersonalCodes.withValidChecksum("3830101000");
  private static final String MAXED_OUT = TestPersonalCodes.withValidChecksum("3820101000");

  @Autowired private ThirdPillarPaymentArrivedJob job;
  @Autowired private AnalyticsThirdPillarTransactionRepository transactionRepository;
  @Autowired private UnitOwnerRepository unitOwnerRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EmailRepository emailRepository;
  @Autowired private EmailPersistenceService emailPersistenceService;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private ThirdPillarPaymentArrivedClaims claims;
  @Autowired private ThirdPillarPaymentArrivedEmailService paymentArrivedEmailService;

  @MockitoBean private EmailService emailService;

  @BeforeEach
  void stubMandrill() {
    given(emailService.newMandrillMessage(any(), any(), any(), any(), any()))
        .willReturn(new MandrillMessage());
    var response = org.mockito.Mockito.mock(MandrillMessageStatus.class);
    given(response.getId()).willReturn("mandrill-id");
    given(response.getStatus()).willReturn("sent");
    given(emailService.send(any(Person.class), any(), any())).willReturn(Optional.of(response));
  }

  @AfterEach
  void cleanUp() {
    transactionRepository.deleteAll();
    unitOwnerRepository.deleteAll();
    emailRepository.deleteAll();
    userRepository.deleteAll();
    jdbcClient.sql("DELETE FROM third_pillar_payment_arrived_claim").update();
    jdbcClient.sql("DELETE FROM saving_fund_payment").update();
  }

  @Test
  void sendsTheEmailOnceToAFirstTimePayerWithAnAccount() {
    saveUser(ACCOUNT_HOLDER, "account.holder@example.com");
    saveOwnPayment(ACCOUNT_HOLDER, LocalDate.now().minusDays(1), new BigDecimal("300.00"));

    job.run();
    job.run();

    verify(emailService, times(1))
        .newMandrillMessage(
            eq("account.holder@example.com"),
            eq("third_pillar_payment_arrived_et"),
            argThat(
                mergeVars ->
                    Boolean.TRUE.equals(mergeVars.get("suggestSecondPillar"))
                        && Boolean.TRUE.equals(mergeVars.get("hasTulevaUser"))
                        && LocalDate.now()
                            .minusDays(1)
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                            .equals(mergeVars.get("paymentDate"))),
            any(),
            any());
    verify(emailService, times(1))
        .send(
            argThat((Person person) -> ACCOUNT_HOLDER.equals(person.getPersonalCode())),
            any(),
            eq("third_pillar_payment_arrived_et"));
    assertThat(sentEmailCount()).isEqualTo(1);
    assertThat(claimCount()).isEqualTo(1);
  }

  @Test
  void neverSuggestsTheSecondPillarToSomeoneAtRetirementAge() {
    saveUser(PENSIONER, "pensioner@example.com");
    saveOwnPayment(PENSIONER, LocalDate.now().minusDays(1), new BigDecimal("100.00"));

    job.run();

    verify(emailService)
        .newMandrillMessage(
            eq("pensioner@example.com"),
            eq("third_pillar_payment_arrived_et"),
            argThat(mergeVars -> Boolean.FALSE.equals(mergeVars.get("suggestSecondPillar"))),
            any(),
            any());
  }

  @Test
  void skipsAnInAppPayerWhoAlreadyGotThePaymentSuccessEmail() {
    User user = saveUser(IN_APP_PAYER, "in.app@example.com");
    emailPersistenceService.save(user, THIRD_PILLAR_PAYMENT_SUCCESS_MANDATE, EmailStatus.SENT);
    saveOwnPayment(IN_APP_PAYER, LocalDate.now().minusDays(1), new BigDecimal("100.00"));

    job.run();

    verify(emailService, never()).send(any(Person.class), any(), any());
  }

  @Test
  void skipsAnInAppMandateSignerWhoIsAlreadyInTheExistingEmailSequence() {
    User user = saveUser(IN_APP_PAYER, "mandate.signer@example.com");
    emailPersistenceService.save(user, THIRD_PILLAR_PAYMENT_REMINDER_MANDATE, EmailStatus.SENT);
    saveOwnPayment(IN_APP_PAYER, LocalDate.now().minusDays(1), new BigDecimal("100.00"));

    job.run();

    verify(emailService, never()).send(any(Person.class), any(), any());
    assertThat(claimCount()).isZero();
  }

  @Test
  void skipsASaverWhoseFirstPaymentWasLongAgo() {
    saveUser(LONG_TIME_SAVER, "long.time@example.com");
    saveOwnPayment(LONG_TIME_SAVER, LocalDate.now().minusYears(2), new BigDecimal("50.00"));
    saveOwnPayment(LONG_TIME_SAVER, LocalDate.now().minusDays(1), new BigDecimal("100.00"));

    job.run();

    verify(emailService, never()).send(any(Person.class), any(), any());
  }

  @Test
  void skipsEmployerPaidContributions() {
    saveUser(EMPLOYER_PAID, "employer.paid@example.com");
    transactionRepository.save(
        exampleTransactionBuilder()
            .personalId(EMPLOYER_PAID)
            .reportingDate(LocalDate.now().minusDays(1))
            .transactionSource(EMPLOYER_SOURCE)
            .transactionValue(new BigDecimal("100.00"))
            .build());

    job.run();

    verify(emailService, never()).send(any(Person.class), any(), any());
  }

  @Test
  void sendsToARegistryOnlyPayerUsingTheirRegistryEmailAndLanguage() {
    saveUnitOwner(REGISTRY_ONLY, "registry.only@example.com", "ENG", "LXK75");
    saveOwnPayment(REGISTRY_ONLY, LocalDate.now().minusDays(2), new BigDecimal("250.00"));

    job.run();

    verify(emailService, times(1))
        .newMandrillMessage(
            eq("registry.only@example.com"),
            eq("third_pillar_payment_arrived_en"),
            argThat(
                mergeVars ->
                    Boolean.TRUE.equals(mergeVars.get("suggestSecondPillar"))
                        && Boolean.FALSE.equals(mergeVars.get("hasTulevaUser"))),
            any(),
            any());
    verify(emailService, times(1))
        .send(
            argThat((Person person) -> REGISTRY_ONLY.equals(person.getPersonalCode())),
            any(),
            eq("third_pillar_payment_arrived_en"));
  }

  @Test
  void fallsBackToTheRegistryEmailWhenTheAccountEmailIsBlank() {
    userRepository.save(
        User.builder()
            .personalCode(ACCOUNT_HOLDER)
            .email("")
            .firstName("First")
            .lastName("Last")
            .createdDate(Instant.now())
            .updatedDate(Instant.now())
            .build());
    saveUnitOwner(ACCOUNT_HOLDER, "registry.fallback@example.com", "EST", "LXK75");
    saveOwnPayment(ACCOUNT_HOLDER, LocalDate.now().minusDays(1), new BigDecimal("100.00"));

    job.run();

    verify(emailService, times(1))
        .newMandrillMessage(
            eq("registry.fallback@example.com"),
            eq("third_pillar_payment_arrived_et"),
            any(),
            any(),
            any());
  }

  @Test
  void skipsWithoutClaimingWhenTheRecipientHasNoName() {
    unitOwnerRepository.save(
        UnitOwner.builder()
            .personalId(REGISTRY_ONLY)
            .snapshotDate(LocalDate.now())
            .dateCreated(java.time.LocalDateTime.now())
            .email("nameless@example.com")
            .languagePreference("EST")
            .build());
    saveOwnPayment(REGISTRY_ONLY, LocalDate.now().minusDays(1), new BigDecimal("100.00"));

    job.run();

    verify(emailService, never()).send(any(Person.class), any(), any());
    assertThat(claimCount()).isZero();
  }

  @Test
  void skipsWithoutClaimingWhenTheRecipientNameIsBlank() {
    unitOwnerRepository.save(
        UnitOwner.builder()
            .personalId(REGISTRY_ONLY)
            .snapshotDate(LocalDate.now())
            .dateCreated(java.time.LocalDateTime.now())
            .firstName("")
            .lastName("")
            .email("blank.name@example.com")
            .languagePreference("EST")
            .build());
    saveOwnPayment(REGISTRY_ONLY, LocalDate.now().minusDays(1), new BigDecimal("100.00"));

    job.run();

    verify(emailService, never()).send(any(Person.class), any(), any());
    assertThat(claimCount()).isZero();
  }

  @Test
  void rejectsASecondClaimForTheSamePerson() {
    assertThat(claims.claim(ACCOUNT_HOLDER)).isTrue();
    assertThat(claims.claim(ACCOUNT_HOLDER)).isFalse();
  }

  @Test
  void doesNotSendWhenTheClaimIsAlreadyTaken() {
    claims.claim(REGISTRY_ONLY);

    boolean sent =
        paymentArrivedEmailService.send(
            new FirstThirdPillarPayment(
                REGISTRY_ONLY,
                "First",
                "Last",
                "already.claimed@example.com",
                "EST",
                new BigDecimal("100.00"),
                LocalDate.now().minusDays(1),
                false,
                true,
                true,
                true,
                false,
                false));

    assertThat(sent).isFalse();
    verify(emailService, never()).send(any(Person.class), any(), any());
  }

  @Test
  void keepsTheClaimAndSendsNothingLaterWhenMandrillFails() {
    saveUser(ACCOUNT_HOLDER, "account.holder@example.com");
    saveOwnPayment(ACCOUNT_HOLDER, LocalDate.now().minusDays(1), new BigDecimal("300.00"));
    given(emailService.send(any(Person.class), any(), any())).willReturn(Optional.empty());

    job.run();
    job.run();

    verify(emailService, times(1)).send(any(Person.class), any(), any());
    assertThat(claimCount()).isEqualTo(1);
    assertThat(sentEmailCount()).isZero();
  }

  @Test
  void suggestsTheSavingsFundOnlyToMaxedOutNonSavers() {
    saveUnitOwner(
        MAXED_OUT,
        builder -> builder.email("maxed.out@example.com").p2choice("TUK75").p2nextRate(6));
    saveOwnPayment(MAXED_OUT, LocalDate.now().minusDays(1), new BigDecimal("400.00"));

    job.run();

    verify(emailService)
        .newMandrillMessage(
            eq("maxed.out@example.com"),
            eq("third_pillar_payment_arrived_et"),
            argThat(
                mergeVars ->
                    Boolean.TRUE.equals(mergeVars.get("suggestSavingsFund"))
                        && Boolean.FALSE.equals(mergeVars.get("suggestSecondPillar"))
                        && Boolean.FALSE.equals(mergeVars.get("suggestPaymentRate"))),
            any(),
            any());
  }

  @Test
  void doesNotSuggestTheSavingsFundToAnExistingSaver() {
    saveUnitOwner(
        MAXED_OUT,
        builder -> builder.email("maxed.out@example.com").p2choice("TUK75").p2nextRate(6));
    saveIssuedSavingsFundPayment(MAXED_OUT);
    saveOwnPayment(MAXED_OUT, LocalDate.now().minusDays(1), new BigDecimal("400.00"));

    job.run();

    verify(emailService)
        .newMandrillMessage(
            eq("maxed.out@example.com"),
            eq("third_pillar_payment_arrived_et"),
            argThat(mergeVars -> Boolean.FALSE.equals(mergeVars.get("suggestSavingsFund"))),
            any(),
            any());
  }

  @Test
  void skipsAnUnderagePayerWithoutClaiming() {
    saveUser(UNDERAGE, "child@example.com");
    saveOwnPayment(UNDERAGE, LocalDate.now().minusDays(1), new BigDecimal("50.00"));

    job.run();

    verifyNoInteractions(emailService);
    assertThat(claimCount()).isZero();
  }

  @Test
  void skipsADeceasedRegistryPersonWithoutClaiming() {
    saveUnitOwner(
        DECEASED,
        builder -> builder.email("estate@example.com").deathDate(LocalDate.now().minusMonths(1)));
    saveOwnPayment(DECEASED, LocalDate.now().minusDays(1), new BigDecimal("100.00"));

    job.run();

    verifyNoInteractions(emailService);
    assertThat(claimCount()).isZero();
  }

  @Test
  void marksASecondPillarLeaverAndSuppressesSecondPillarNudges() {
    saveUnitOwner(
        SECOND_PILLAR_LEAVER,
        builder -> builder.email("leaver@example.com").p2ravaStatus("R").p2choice("LXK00"));
    saveOwnPayment(SECOND_PILLAR_LEAVER, LocalDate.now().minusDays(1), new BigDecimal("200.00"));

    job.run();

    verify(emailService)
        .newMandrillMessage(
            eq("leaver@example.com"),
            eq("third_pillar_payment_arrived_et"),
            argThat(
                mergeVars ->
                    Boolean.TRUE.equals(mergeVars.get("leftSecondPillar"))
                        && Boolean.FALSE.equals(mergeVars.get("suggestSecondPillar"))
                        && Boolean.FALSE.equals(mergeVars.get("suggestPaymentRate"))),
            any(),
            any());
  }

  private void saveIssuedSavingsFundPayment(String personalCode) {
    jdbcClient
        .sql(
            """
            INSERT INTO saving_fund_payment
              (id, party_type, party_code, amount, currency, status, created_at, status_changed_at)
            VALUES
              (:id, 'PERSON', :code, 100.00, 'EUR', 'ISSUED', now(), now())
            """)
        .param("id", java.util.UUID.randomUUID())
        .param("code", personalCode)
        .update();
  }

  private void saveOwnPayment(String personalCode, LocalDate date, BigDecimal amount) {
    transactionRepository.save(
        exampleTransactionBuilder()
            .personalId(personalCode)
            .reportingDate(date)
            .transactionSource(OWN_MONEY_SOURCE)
            .transactionValue(amount)
            .build());
  }

  private User saveUser(String personalCode, String email) {
    return userRepository.save(
        User.builder()
            .personalCode(personalCode)
            .email(email)
            .firstName("First")
            .lastName("Last")
            .createdDate(Instant.now())
            .updatedDate(Instant.now())
            .build());
  }

  private void saveUnitOwner(String personalCode, String email, String language, String p2Choice) {
    saveUnitOwner(
        personalCode,
        builder -> builder.email(email).languagePreference(language).p2choice(p2Choice));
  }

  private void saveUnitOwner(
      String personalCode,
      java.util.function.UnaryOperator<UnitOwner.UnitOwnerBuilder> customizer) {
    unitOwnerRepository.save(
        customizer
            .apply(
                UnitOwner.builder()
                    .personalId(personalCode)
                    .snapshotDate(LocalDate.now())
                    .dateCreated(java.time.LocalDateTime.now())
                    .firstName("Registry")
                    .lastName("Person"))
            .build());
  }

  private int sentEmailCount() {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM email WHERE type = :type")
        .param("type", THIRD_PILLAR_PAYMENT_ARRIVED.name())
        .query(Integer.class)
        .single();
  }

  private int claimCount() {
    return jdbcClient
        .sql("SELECT COUNT(*) FROM third_pillar_payment_arrived_claim")
        .query(Integer.class)
        .single();
  }
}
