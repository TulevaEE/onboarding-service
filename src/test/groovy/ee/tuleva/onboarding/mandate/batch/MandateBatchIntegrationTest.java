package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.JwtTokenGenerator.getHeaders;
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.epis.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.mandate.MandateType.FUND_PENSION_OPENING;
import static ee.tuleva.onboarding.mandate.MandateType.PARTIAL_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.MandateType.PAYMENT_RATE_CHANGE;
import static ee.tuleva.onboarding.mandate.MandateType.REJOIN;
import static ee.tuleva.onboarding.mandate.MandateType.REJOIN_CANCELLATION;
import static ee.tuleva.onboarding.mandate.MandateType.SELECTION;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.INITIALIZED;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.SIGNED;
import static ee.tuleva.onboarding.mandate.details.PaymentRateChangeMandateDetails.PaymentRate.SIX;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.WITHDRAWALS;
import static ee.tuleva.onboarding.notification.email.EmailType.BATCH_FAILED;
import static ee.tuleva.onboarding.notification.email.EmailType.CONTACT_DETAILS_CHANGED;
import static ee.tuleva.onboarding.notification.email.EmailType.SECOND_PILLAR_REJOIN;
import static ee.tuleva.onboarding.notification.email.EmailType.SECOND_PILLAR_REJOIN_CANCELLATION;
import static ee.tuleva.onboarding.notification.email.EmailType.WITHDRAWAL_BATCH;
import static ee.tuleva.onboarding.signature.SignatureStatus.OUTSTANDING_TRANSACTION;
import static ee.tuleva.onboarding.user.UserContactDetailsTestFixture.withContactDetails;
import static java.time.ZoneOffset.UTC;
import static java.util.Locale.ENGLISH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;

import com.microtripit.mandrillapp.lutung.model.LutungGsonUtils;
import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.aml.AmlAutoChecker;
import ee.tuleva.onboarding.applicationtype.ApplicationType;
import ee.tuleva.onboarding.auth.SecurityContextRunner;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.CashFlowStatement;
import ee.tuleva.onboarding.epis.ContactDetails;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.error.response.ErrorResponse;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateFixture;
import ee.tuleva.onboarding.mandate.MandateProcessResult;
import ee.tuleva.onboarding.mandate.MandateProcessResult.MandateProcessOutcome;
import ee.tuleva.onboarding.mandate.MandateRepository;
import ee.tuleva.onboarding.mandate.MandateService;
import ee.tuleva.onboarding.mandate.MandateSubmissionCommand;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.application.ApplicationCancellationResponse;
import ee.tuleva.onboarding.mandate.application.ApplicationSnapshot;
import ee.tuleva.onboarding.mandate.application.ApplicationStatus;
import ee.tuleva.onboarding.mandate.batch.poller.MandateBatchProcessingPoller;
import ee.tuleva.onboarding.mandate.details.PaymentRateChangeMandateDetails;
import ee.tuleva.onboarding.mandate.details.RejoinCancellationMandateDetails;
import ee.tuleva.onboarding.mandate.details.RejoinMandateDetails;
import ee.tuleva.onboarding.mandate.details.SelectionMandateDetails;
import ee.tuleva.onboarding.mandate.exception.MandateProcessingException;
import ee.tuleva.onboarding.mandate.generic.MandateDto;
import ee.tuleva.onboarding.mandate.processor.MandateProcess;
import ee.tuleva.onboarding.mandate.processor.MandateProcessRepository;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.signature.MobileIdSignatureSession;
import ee.tuleva.onboarding.signature.SignatureService;
import ee.tuleva.onboarding.time.ClockHolder;
import ee.tuleva.onboarding.user.RecentlyReplacedEmails;
import ee.tuleva.onboarding.user.UserRepository;
import ee.tuleva.onboarding.withdrawals.WithdrawalEligibilityDto;
import ee.tuleva.onboarding.withdrawals.WithdrawalEligibilityService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.util.Streamable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
class MandateBatchIntegrationTest {

  private static final String OLD_EMAIL = "old-address@example.com";
  private static final String NEW_EMAIL = "new-address@example.com";
  private static final Instant EMAIL_CHANGED_AT = Instant.parse("2026-03-02T10:00:00Z");

  @Autowired private RestTestClient restTestClient;

  @Autowired private JsonMapper mapper;

  @Autowired private MandateBatchRepository mandateBatchRepository;
  @Autowired private MandateRepository mandateRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private MandateBatchService mandateBatchService;
  @Autowired private MandateProcessRepository mandateProcessRepository;
  @Autowired private MandateService mandateService;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private MandateBatchCompletion mandateBatchCompletion;
  @Autowired private SecurityContextRunner securityContextRunner;
  @Autowired private EmailPersistenceService emailPersistenceService;
  @Autowired private JdbcClient jdbcClient;

  @MockitoBean private EpisService episService;
  @MockitoBean private AmlAutoChecker amlAutoChecker;
  @MockitoBean private WithdrawalEligibilityService withdrawalEligibilityService;
  @MockitoBean private OperationsNotificationService notificationService;
  @MockitoSpyBean private EmailService emailService;
  @MockitoBean private SignatureService signatureService;
  @MockitoSpyBean private RecentlyReplacedEmails recentlyReplacedEmails;
  @MockitoSpyBean private MandateBatchProcessingPoller mandateBatchProcessingPoller;

  // The JWT authenticates samplePerson, and the auth filter creates that user on the server
  // thread, which no test-side transaction can roll back. It outlives the test in the shared
  // database and collides with every later test that inserts the same personal code.
  @AfterEach
  void cleanup() {
    ClockHolder.setDefaultClock();
    mandateProcessRepository.deleteAll();
    mandateRepository.deleteAll();
    mandateBatchRepository.deleteAll();
    jdbcClient
        .sql(
            "DELETE FROM email WHERE personal_code = :personalCode"
                + " AND type IN ('CONTACT_DETAILS_CHANGED', 'PREVIOUS_CONTACT_ACTIVITY')")
        .param("personalCode", samplePerson().getPersonalCode())
        .update();
    userRepository
        .findByPersonalCode(samplePerson().getPersonalCode())
        .ifPresent(
            user -> {
              jdbcClient
                  .sql("DELETE FROM user_contact_change WHERE user_id = :userId")
                  .param("userId", user.getId())
                  .update();
              userRepository.delete(user);
            });
  }

  void assertCorrectResponse(byte[] responseBody) throws Exception {
    var jsonNode = mapper.readTree(responseBody);

    MandateDto[] responseMandateDtos =
        mapper.readValue(jsonNode.get("mandates").toString(), MandateDto[].class);

    assertThat(responseMandateDtos.length).isEqualTo(2);
    assertThat(
            Streamable.of(responseMandateDtos).stream()
                .filter(mandateDto -> mandateDto.getMandateType().equals(PARTIAL_WITHDRAWAL))
                .findFirst())
        .isPresent();
    assertThat(
            Streamable.of(responseMandateDtos).stream()
                .filter(mandateDto -> mandateDto.getMandateType().equals(FUND_PENSION_OPENING))
                .findFirst())
        .isPresent();
  }

  void assertCanReadMandateBatch() {
    var readMandateBatches = Streamable.of(mandateBatchRepository.findAll()).toList();
    assertThat(readMandateBatches.size()).isEqualTo(1);
    MandateBatch firstMandateBatch = readMandateBatches.getFirst();

    assertThat(firstMandateBatch.getStatus()).isEqualTo(INITIALIZED);
    var firstMandateBatchMandates = firstMandateBatch.getMandates();

    assertThat(firstMandateBatchMandates.size()).isEqualTo(2);
    assertThat(
            Streamable.of(firstMandateBatchMandates).stream()
                .filter(mandate -> mandate.getMandateType().equals(PARTIAL_WITHDRAWAL))
                .findFirst())
        .isPresent();
    assertThat(
            Streamable.of(firstMandateBatchMandates).stream()
                .filter(mandate -> mandate.getMandateType().equals(FUND_PENSION_OPENING))
                .findFirst())
        .isPresent();
  }

  @Test
  void testMandateCreation() throws Exception {
    var headers = getHeaders();

    var aFundPensionOpeningMandateDetails = MandateFixture.aFundPensionOpeningMandateDetails;
    var aPartialWithdrawalMandateDetails = MandateFixture.aPartialWithdrawalMandateDetails;

    var aDto =
        MandateBatchDto.builder()
            .mandates(
                List.of(
                    MandateDto.builder().details(aFundPensionOpeningMandateDetails).build(),
                    MandateDto.builder().details(aPartialWithdrawalMandateDetails).build()))
            .build();

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(true)
            .canWithdrawThirdPillarWithReducedTax(true)
            .age(65)
            .recommendedDurationYears(20)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(any()))
        .thenReturn(aWithdrawalEligibility);
    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());

    var responseBody =
        restTestClient
            .post()
            .uri("/v1/mandate-batches")
            .headers(h -> h.addAll(headers))
            .body(aDto)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

    assertCorrectResponse(responseBody);
    assertCanReadMandateBatch();
  }

  @Test
  void withdrawalBatchCreationSendsExactlyOneSlackNotificationInUnifiedFormat() {
    var headers = getHeaders();

    var aDto =
        MandateBatchDto.builder()
            .mandates(
                List.of(
                    MandateDto.builder()
                        .details(MandateFixture.aFundPensionOpeningMandateDetails)
                        .build(),
                    MandateDto.builder()
                        .details(MandateFixture.aPartialWithdrawalMandateDetails)
                        .build()))
            .build();

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(true)
            .canWithdrawThirdPillarWithReducedTax(true)
            .age(65)
            .recommendedDurationYears(20)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(any()))
        .thenReturn(aWithdrawalEligibility);
    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());

    restTestClient
        .post()
        .uri("/v1/mandate-batches")
        .headers(h -> h.addAll(headers))
        .body(aDto)
        .exchange()
        .expectStatus()
        .isOk();

    verify(notificationService, times(1))
        .sendMessage(
            argThat(
                message ->
                    message.startsWith("Withdrawal mandate batch created: age=")
                        && message.contains("pillars=")
                        && message.contains("withdrawalTypes=")
                        && message.contains("mandateBatchId=")),
            eq(WITHDRAWALS));
  }

  @Test
  void testMandateCreationBeforeRetirementAge() {
    var headers = getHeaders();

    var aFundPensionOpeningMandateDetails = MandateFixture.aFundPensionOpeningMandateDetails;
    var aPartialWithdrawalMandateDetails = MandateFixture.aPartialWithdrawalMandateDetails;

    var aDto =
        MandateBatchDto.builder()
            .mandates(
                List.of(
                    MandateDto.builder().details(aFundPensionOpeningMandateDetails).build(),
                    MandateDto.builder().details(aPartialWithdrawalMandateDetails).build()))
            .build();

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(false)
            .canWithdrawThirdPillarWithReducedTax(false)
            .age(30)
            .recommendedDurationYears(55)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(any()))
        .thenReturn(aWithdrawalEligibility);
    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());

    restTestClient
        .post()
        .uri("/v1/mandate-batches")
        .headers(h -> h.addAll(headers))
        .body(aDto)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(200));

    assertThat(Streamable.of(mandateBatchRepository.findAll()).toList().size()).isEqualTo(0);
  }

  @Test
  void testMandateCreationThirdPillarSpecialCase() throws Exception {
    var headers = getHeaders();

    var aFundPensionOpeningMandateDetails =
        MandateFixture.aThirdPillarFundPensionOpeningMandateDetails;
    var aPartialWithdrawalMandateDetails =
        MandateFixture.aThirdPillarPartialWithdrawalMandateDetails;

    var aDto =
        MandateBatchDto.builder()
            .mandates(
                List.of(
                    MandateDto.builder().details(aFundPensionOpeningMandateDetails).build(),
                    MandateDto.builder().details(aPartialWithdrawalMandateDetails).build()))
            .build();

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(false)
            .canWithdrawThirdPillarWithReducedTax(true)
            .age(56)
            .recommendedDurationYears(24)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(any()))
        .thenReturn(aWithdrawalEligibility);
    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());

    var responseBody =
        restTestClient
            .post()
            .uri("/v1/mandate-batches")
            .headers(h -> h.addAll(headers))
            .body(aDto)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(byte[].class)
            .returnResult()
            .getResponseBody();

    assertCorrectResponse(responseBody);
    assertCanReadMandateBatch();
  }

  @Test
  void testMandateCreationThirdPillarSpecialCaseDisabled() {
    var headers = getHeaders();

    var aFundPensionOpeningMandateDetails =
        MandateFixture.aThirdPillarFundPensionOpeningMandateDetails;
    var aPartialWithdrawalMandateDetails =
        MandateFixture.aThirdPillarPartialWithdrawalMandateDetails;

    var aDto =
        MandateBatchDto.builder()
            .mandates(
                List.of(
                    MandateDto.builder().details(aFundPensionOpeningMandateDetails).build(),
                    MandateDto.builder().details(aPartialWithdrawalMandateDetails).build()))
            .build();

    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(false)
            .canWithdrawThirdPillarWithReducedTax(false)
            .age(56)
            .recommendedDurationYears(24)
            .arrestsOrBankruptciesPresent(false)
            .build();

    when(withdrawalEligibilityService.getWithdrawalEligibility(any()))
        .thenReturn(aWithdrawalEligibility);
    when(episService.getCashFlowStatement(any(), any(), any())).thenReturn(new CashFlowStatement());
    when(episService.getContactDetails(any())).thenReturn(contactDetailsFixture());

    restTestClient
        .post()
        .uri("/v1/mandate-batches")
        .headers(h -> h.addAll(headers))
        .body(aDto)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(200));

    assertThat(Streamable.of(mandateBatchRepository.findAll()).toList().size()).isEqualTo(0);
  }

  @Test
  void pollerCompletesASignedBatchAsItsOwnerSoTheContactUpdateReachesEpisAndTheBatchEmailIsSent() {
    var aWithdrawalEligibility =
        WithdrawalEligibilityDto.builder()
            .hasReachedEarlyRetirementAge(true)
            .canWithdrawThirdPillarWithReducedTax(true)
            .age(65)
            .recommendedDurationYears(20)
            .arrestsOrBankruptciesPresent(false)
            .build();
    given(withdrawalEligibilityService.getWithdrawalEligibility(any()))
        .willReturn(aWithdrawalEligibility);
    given(episService.getCashFlowStatement(any(), any(), any()))
        .willReturn(new CashFlowStatement());
    given(episService.getContactDetails(any())).willReturn(contactDetailsFixture());
    var personalCodesEpisWasCalledAs = new CopyOnWriteArrayList<String>();
    given(episService.updateContactDetails(any(), any()))
        .willAnswer(
            invocation -> {
              personalCodesEpisWasCalledAs.add(authenticatedPersonalCode());
              return invocation.getArgument(1, ContactDetails.class);
            });
    restTestClient
        .post()
        .uri("/v1/mandate-batches")
        .headers(h -> h.addAll(getHeaders()))
        .body(
            MandateBatchDto.builder()
                .mandates(
                    List.of(
                        MandateDto.builder()
                            .details(MandateFixture.aFundPensionOpeningMandateDetails)
                            .build(),
                        MandateDto.builder()
                            .details(MandateFixture.aPartialWithdrawalMandateDetails)
                            .build()))
                .build())
        .exchange()
        .expectStatus()
        .isOk();
    var batch = Streamable.of(mandateBatchRepository.findAll()).toList().getFirst();
    batch.setFile(new byte[] {1, 2, 3});
    batch.setStatus(SIGNED);
    mandateBatchRepository.save(batch);
    mandateRepository.findAll().forEach(this::recordAcceptedByEpis);

    mandateBatchProcessingPoller.startPollingForBatchProcessingFinished(batch, Locale.ENGLISH);
    mandateBatchProcessingPoller.processQueue();

    var ownerPersonalCode = samplePerson().getPersonalCode();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                then(emailService)
                    .should()
                    .send(
                        argThat(person -> person.getPersonalCode().equals(ownerPersonalCode)),
                        argThat(
                            message ->
                                message
                                    .getAttachments()
                                    .getFirst()
                                    .getName()
                                    .endsWith("_avaldused_" + batch.getId() + ".asice")),
                        eq(WITHDRAWAL_BATCH.getTemplateName(Locale.ENGLISH))));
    assertThat(personalCodesEpisWasCalledAs).containsExactly(ownerPersonalCode, ownerPersonalCode);
  }

  @Test
  void signingARejoinSendsTheRejoinTheSixPercentRateAndTheFundToEpisAndEmailsTheRejoin() {
    given(episService.getCashFlowStatement(any(), any(), any()))
        .willReturn(new CashFlowStatement());
    given(episService.getContactDetails(any())).willReturn(contactDetailsFixture());
    given(episService.updateContactDetails(any(), any()))
        .willAnswer(invocation -> invocation.getArgument(1));
    var applicationsSentToEpis = new CopyOnWriteArrayList<MandateType>();
    given(episService.sendMandateV2(any()))
        .willAnswer(
            invocation -> {
              MandateSubmissionCommand<?> command = invocation.getArgument(0);
              applicationsSentToEpis.add(command.submission().getMandateType());
              return new MandateProcessResult(
                  List.of(new MandateProcessOutcome(command.processId(), true, null)));
            });

    restTestClient
        .post()
        .uri("/v1/mandate-batches")
        .headers(headers -> headers.addAll(getHeaders()))
        .body(
            MandateBatchDto.builder()
                .mandates(
                    List.of(
                        MandateDto.builder().details(new RejoinMandateDetails()).build(),
                        MandateDto.builder()
                            .details(new PaymentRateChangeMandateDetails(SIX))
                            .build(),
                        MandateDto.builder()
                            .details(new SelectionMandateDetails("EE3600109435"))
                            .build()))
                .build())
        .exchange()
        .expectStatus()
        .isOk();
    var batch = Streamable.of(mandateBatchRepository.findAll()).toList().getFirst();
    var signer = userRepository.findByPersonalCode(samplePerson().getPersonalCode()).orElseThrow();
    userRepository.save(withContactDetails(signer, "rejoiner@example.com", "+37255555555"));
    given(signatureService.getSignedFile(any(MobileIdSignatureSession.class), any()))
        .willReturn(new byte[] {1, 2, 3});

    mandateBatchService.finalizeMobileSignature(
        signer.getId(), batch.getId(), mock(MobileIdSignatureSession.class), Locale.ENGLISH);
    mandateBatchProcessingPoller.processQueue();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                then(emailService)
                    .should()
                    .send(
                        argThat(
                            person ->
                                person.getPersonalCode().equals(samplePerson().getPersonalCode())),
                        any(),
                        eq(SECOND_PILLAR_REJOIN.getTemplateName(Locale.ENGLISH))));
    then(emailService)
        .should()
        .newMandrillMessage(
            any(),
            eq(SECOND_PILLAR_REJOIN.getTemplateName(Locale.ENGLISH)),
            argThat(
                mergeVars ->
                    String.valueOf(mergeVars.get("contributionStartDate"))
                        .matches("(January|May|September) 1, \\d{4}")),
            any(),
            any());
    assertThat(applicationsSentToEpis)
        .containsExactlyInAnyOrder(REJOIN, PAYMENT_RATE_CHANGE, SELECTION);
    then(notificationService).shouldHaveNoInteractions();
  }

  @Test
  void signingARejoinWithNoEmailOrPhoneOnFileStillSendsEveryApplicationToEpis() {
    given(episService.getCashFlowStatement(any(), any(), any()))
        .willReturn(new CashFlowStatement());
    given(episService.getContactDetails(any())).willReturn(contactDetailsFixture());
    given(episService.updateContactDetails(any(), any()))
        .willAnswer(invocation -> invocation.getArgument(1));
    var applicationsSentToEpis = new CopyOnWriteArrayList<MandateType>();
    given(episService.sendMandateV2(any()))
        .willAnswer(
            invocation -> {
              MandateSubmissionCommand<?> command = invocation.getArgument(0);
              applicationsSentToEpis.add(command.submission().getMandateType());
              return new MandateProcessResult(
                  List.of(new MandateProcessOutcome(command.processId(), true, null)));
            });
    var batch = createRejoinBatch();
    var signer = userRepository.findByPersonalCode(samplePerson().getPersonalCode()).orElseThrow();
    userRepository.save(withContactDetails(signer, null, null));
    given(signatureService.getSignedFile(any(MobileIdSignatureSession.class), any()))
        .willReturn(new byte[] {1, 2, 3});

    var status =
        mandateBatchService.finalizeMobileSignature(
            signer.getId(), batch.getId(), mock(MobileIdSignatureSession.class), Locale.ENGLISH);

    assertThat(status).isEqualTo(OUTSTANDING_TRANSACTION);
    assertThat(applicationsSentToEpis)
        .containsExactlyInAnyOrder(REJOIN, PAYMENT_RATE_CHANGE, SELECTION);
    mandateBatchProcessingPoller.processQueue();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                then(emailService)
                    .should()
                    .send(any(), any(), eq(SECOND_PILLAR_REJOIN.getTemplateName(Locale.ENGLISH))));
  }

  @Test
  void aSignedBatchWhoseApplicationsNeverReachedEpisIsReportedAsNotSentInsteadOfSigned() {
    given(episService.getCashFlowStatement(any(), any(), any()))
        .willReturn(new CashFlowStatement());
    given(episService.getContactDetails(any())).willReturn(contactDetailsFixture());
    var batch = createRejoinBatch();
    batch.setFile(new byte[] {1, 2, 3});
    batch.setStatus(SIGNED);
    mandateBatchRepository.save(batch);
    var signer = userRepository.findByPersonalCode(samplePerson().getPersonalCode()).orElseThrow();
    var notSent =
        ErrorResponse.builder()
            .code("mandate.processing.error.not.sent")
            .message("Mandate was not sent to EPIS")
            .build();

    assertThatThrownBy(
            () ->
                mandateBatchService.finalizeMobileSignature(
                    signer.getId(),
                    batch.getId(),
                    mock(MobileIdSignatureSession.class),
                    Locale.ENGLISH))
        .isInstanceOfSatisfying(
            MandateProcessingException.class,
            rejection ->
                assertThat(rejection.getErrorsResponse())
                    .isEqualTo(new ErrorsResponse(List.of(notSent))));
  }

  private MandateBatch createRejoinBatch() {
    restTestClient
        .post()
        .uri("/v1/mandate-batches")
        .headers(headers -> headers.addAll(getHeaders()))
        .body(
            MandateBatchDto.builder()
                .mandates(
                    List.of(
                        MandateDto.builder().details(new RejoinMandateDetails()).build(),
                        MandateDto.builder()
                            .details(new PaymentRateChangeMandateDetails(SIX))
                            .build(),
                        MandateDto.builder()
                            .details(new SelectionMandateDetails("EE3600109435"))
                            .build()))
                .build())
        .exchange()
        .expectStatus()
        .isOk();
    return Streamable.of(mandateBatchRepository.findAll()).toList().getFirst();
  }

  @Test
  void
      aListedPendingRejoinIsCanceledByFilingTheLeaveApplicationAndIsThenNeitherListedNorCancelableAgain() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-03-02T10:00:00Z"), UTC));
    given(episService.getApplications(any()))
        .willReturn(List.of(pendingRejoinFiledOn(Instant.parse("2026-03-01T22:00:00Z"))));
    given(episService.getCashFlowStatement(any(), any(), any()))
        .willReturn(new CashFlowStatement());
    given(episService.getContactDetails(any())).willReturn(contactDetailsFixture());
    given(episService.updateContactDetails(any(), any()))
        .willAnswer(invocation -> invocation.getArgument(1));
    var applicationsSentToEpis = new CopyOnWriteArrayList<MandateType>();
    given(episService.sendMandateV2(any()))
        .willAnswer(
            invocation -> {
              MandateSubmissionCommand<?> command = invocation.getArgument(0);
              applicationsSentToEpis.add(command.submission().getMandateType());
              return new MandateProcessResult(
                  List.of(new MandateProcessOutcome(command.processId(), true, null)));
            });

    restTestClient
        .get()
        .uri("/v1/applications?status=PENDING")
        .headers(headers -> headers.addAll(getHeaders()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[?(@.type == 'REJOIN')]")
        .isEqualTo(
            List.of(
                Map.of(
                    "id", 321,
                    "type", "REJOIN",
                    "status", "PENDING",
                    "creationTime", "2026-03-01T22:00:00Z",
                    "details",
                        Map.of(
                            "cancellationDeadline", "2026-03-31T20:59:59.999999999Z",
                            "fulfillmentDate", "2026-09-01"))));
    var mandateId = draftRejoinCancellation();

    signWithMobileId(mandateId);

    assertThat(applicationsSentToEpis).containsExactly(REJOIN_CANCELLATION);
    then(emailService)
        .should()
        .send(
            argThat(person -> person.getPersonalCode().equals(samplePerson().getPersonalCode())),
            argThat(
                message ->
                    message
                        .getAttachments()
                        .getFirst()
                        .getName()
                        .endsWith("_avaldus_" + mandateId + ".asice")),
            eq(SECOND_PILLAR_REJOIN_CANCELLATION.getTemplateName(Locale.ENGLISH)));
    restTestClient
        .get()
        .uri("/v1/applications?status=PENDING")
        .headers(headers -> headers.addAll(getHeaders()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[?(@.type == 'REJOIN')]")
        .isEmpty();
    restTestClient
        .post()
        .uri("/v1/applications/321/cancellations")
        .headers(headers -> headers.addAll(getHeaders()))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void aRejoinWhoseFilingWindowHasClosedCannotBeCanceled() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), UTC));
    given(episService.getApplications(any()))
        .willReturn(List.of(pendingRejoinFiledOn(Instant.parse("2026-03-01T22:00:00Z"))));
    given(episService.getContactDetails(any())).willReturn(contactDetailsFixture());

    restTestClient
        .post()
        .uri("/v1/applications/321/cancellations")
        .headers(headers -> headers.addAll(getHeaders()))
        .exchange()
        .expectStatus()
        .isBadRequest();

    assertThat(mandateRepository.findAll()).isEmpty();
  }

  @Test
  void aBatchRefusesTheLeaveApplicationThatCancelsARejoinAsAnUnsupportedMandateType() {
    given(episService.getCashFlowStatement(any(), any(), any()))
        .willReturn(new CashFlowStatement());
    given(episService.getContactDetails(any())).willReturn(contactDetailsFixture());

    restTestClient
        .post()
        .uri("/v1/mandate-batches")
        .headers(headers -> headers.addAll(getHeaders()))
        .body(
            MandateBatchDto.builder()
                .mandates(
                    List.of(
                        MandateDto.builder()
                            .details(new RejoinCancellationMandateDetails())
                            .build()))
                .build())
        .exchange()
        .expectStatus()
        .isBadRequest();

    assertThat(mandateRepository.findAll()).isEmpty();
  }

  @Test
  void aRejoinPensionikeskusNoLongerReportsAsPendingIsListedAndFoundEvenThoughItWasCanceled() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-03-02T10:00:00Z"), UTC));
    var rejoinDate = Instant.parse("2026-03-01T22:00:00Z");
    given(episService.getApplications(any())).willReturn(List.of(pendingRejoinFiledOn(rejoinDate)));
    stubEpisAcceptingTheCancellation();
    signWithMobileId(draftRejoinCancellation());
    given(episService.getApplications(any()))
        .willReturn(List.of(rejoinFiledOn(rejoinDate, ApplicationStatus.COMPLETE)));

    restTestClient
        .get()
        .uri("/v1/applications?status=COMPLETE")
        .headers(headers -> headers.addAll(getHeaders()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[?(@.type == 'REJOIN')].id")
        .isEqualTo(List.of(321));
    restTestClient
        .get()
        .uri("/v1/applications/321")
        .headers(headers -> headers.addAll(getHeaders()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("COMPLETE");
  }

  @Test
  void aRejoinCancellationDraftedBeforeTheFilingWindowClosedCannotStartSigningAfterIt() {
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-03-31T20:58:00Z"), UTC));
    given(episService.getApplications(any()))
        .willReturn(List.of(pendingRejoinFiledOn(Instant.parse("2026-03-01T22:00:00Z"))));
    stubEpisAcceptingTheCancellation();
    var mandateId = draftRejoinCancellation();
    ClockHolder.setClock(Clock.fixed(Instant.parse("2026-03-31T21:00:30Z"), UTC));

    restTestClient
        .put()
        .uri("/v1/mandates/" + mandateId + "/signature/smart-id")
        .headers(headers -> headers.addAll(getHeaders()))
        .exchange()
        .expectStatus()
        .isBadRequest();

    then(signatureService).shouldHaveNoInteractions();
  }

  @Test
  void changingTheEmailThroughTheApiTellsOnlyTheAddressItReplacedAndRecordsTheNotice() {
    willReturn(Optional.of(acceptedFor(OLD_EMAIL)))
        .given(emailService)
        .send(any(), argThat(addressedTo(OLD_EMAIL)), eq("contact_details_changed_en"));

    changeEmailThroughTheApi(OLD_EMAIL);
    changeEmailThroughTheApi(NEW_EMAIL);

    then(emailService)
        .should()
        .send(
            argThat(person -> person.getPersonalCode().equals(samplePerson().getPersonalCode())),
            argThat(addressedTo(OLD_EMAIL)),
            eq("contact_details_changed_en"));
    then(emailService).should(times(1)).send(any(), any(), eq("contact_details_changed_en"));
    assertThat(
            emailPersistenceService.hasPendingOrSentEmail(samplePerson(), CONTACT_DETAILS_CHANGED))
        .isTrue();
  }

  @Test
  void
      aLetterCaseEditOfTheEmailWithANewPhoneNumberThroughTheApiTellsTheCurrentAddressThePhoneChanged() {
    changeEmailThroughTheApi(NEW_EMAIL);

    changeContactDetailsThroughTheApi("New-Address@example.com", "+37255555555");

    then(emailService)
        .should()
        .newMandrillMessage(
            eq("New-Address@example.com"),
            eq("contact_details_changed_en"),
            argThat(
                mergeVars ->
                    Boolean.FALSE.equals(mergeVars.get("emailChanged"))
                        && Boolean.TRUE.equals(mergeVars.get("phoneChanged"))
                        && Boolean.FALSE.equals(mergeVars.get("previousAddress"))),
            any());
    then(emailService)
        .should()
        .send(
            any(),
            argThat(addressedTo("New-Address@example.com")),
            eq("contact_details_changed_en"));
    then(emailService).should(times(1)).send(any(), any(), eq("contact_details_changed_en"));
  }

  @Test
  void
      aWithdrawalSignedWithinTheWindowAfterAnEmailChangeIsNoticedAtTheReplacedAddressOnSigningAndStillConfirmedAtTheNewOne() {
    changeEmailOn(EMAIL_CHANGED_AT);
    ClockHolder.setClock(Clock.fixed(EMAIL_CHANGED_AT.plus(Duration.ofDays(27)), UTC));
    willReturn(Optional.of(acceptedFor(OLD_EMAIL)))
        .given(emailService)
        .send(any(), argThat(addressedTo(OLD_EMAIL)), eq("previous_contact_activity_en"));

    var batch = signedWithdrawalBatchPensionikeskusRejects(Set.of());

    then(emailService)
        .should()
        .send(
            argThat(person -> person.getPersonalCode().equals(samplePerson().getPersonalCode())),
            argThat(addressedTo(OLD_EMAIL)),
            eq("previous_contact_activity_en"));
    completeAsOwner(batch);
    then(emailService)
        .should()
        .send(
            argThat(person -> person.getPersonalCode().equals(samplePerson().getPersonalCode())),
            argThat(addressedTo(NEW_EMAIL)),
            eq("withdrawal_batch_en"));
    then(emailService).should(times(1)).send(any(), any(), eq("previous_contact_activity_en"));
  }

  @Test
  void aWithdrawalSignedOnTheLastDayOfTheWindowIsNoticedEvenWhenItsSubmissionRunsPastIt() {
    changeEmailOn(EMAIL_CHANGED_AT);
    ClockHolder.setClock(Clock.fixed(EMAIL_CHANGED_AT.plus(Duration.ofDays(28)), UTC));

    signedWithdrawalBatchSubmittedTo(
        command -> {
          ClockHolder.setClock(Clock.fixed(EMAIL_CHANGED_AT.plus(Duration.ofDays(29)), UTC));
          return acceptance(command);
        });

    then(emailService)
        .should()
        .send(any(), argThat(addressedTo(OLD_EMAIL)), eq("previous_contact_activity_en"));
  }

  @Test
  void aWithdrawalSignedAfterTheWindowAfterAnEmailChangeIsOnlyConfirmedAtTheNewAddress() {
    changeEmailOn(EMAIL_CHANGED_AT);
    ClockHolder.setClock(Clock.fixed(EMAIL_CHANGED_AT.plus(Duration.ofDays(29)), UTC));

    completeAsOwner(signedWithdrawalBatchPensionikeskusRejects(Set.of()));

    then(emailService)
        .should()
        .send(any(), argThat(addressedTo(NEW_EMAIL)), eq("withdrawal_batch_en"));
    then(emailService).should(never()).send(any(), any(), eq("previous_contact_activity_en"));
  }

  @Test
  void aWithdrawalPensionikeskusPartlyRejectsIsStillNoticedAtTheReplacedAddress() {
    changeEmailOn(EMAIL_CHANGED_AT);
    willReturn(Optional.of(acceptedFor(OLD_EMAIL)))
        .given(emailService)
        .send(any(), argThat(addressedTo(OLD_EMAIL)), eq("previous_contact_activity_en"));
    var batch = signedWithdrawalBatchPensionikeskusRejects(Set.of(PARTIAL_WITHDRAWAL));

    assertThatThrownBy(() -> completeAsOwner(batch)).isInstanceOf(MandateProcessingException.class);

    then(emailService)
        .should(times(1))
        .send(any(), argThat(addressedTo(OLD_EMAIL)), eq("previous_contact_activity_en"));
    then(emailService)
        .should()
        .sendFromBackgroundJob(
            any(), argThat(addressedTo(NEW_EMAIL)), eq(BATCH_FAILED.getTemplateName(ENGLISH)));
    then(emailService).should(never()).send(any(), any(), eq("withdrawal_batch_en"));
  }

  @Test
  void aWithdrawalPensionikeskusRejectsEntirelyIsStillNoticedAtTheReplacedAddress() {
    changeEmailOn(EMAIL_CHANGED_AT);
    var batch =
        signedWithdrawalBatchPensionikeskusRejects(
            Set.of(PARTIAL_WITHDRAWAL, FUND_PENSION_OPENING));

    assertThatThrownBy(() -> completeAsOwner(batch)).isInstanceOf(MandateProcessingException.class);

    then(emailService)
        .should(times(1))
        .send(any(), argThat(addressedTo(OLD_EMAIL)), eq("previous_contact_activity_en"));
    then(emailService).should(never()).send(any(), any(), eq("withdrawal_batch_en"));
  }

  @Test
  void aWithdrawalWhoseCompletionFailsAtEpisAfterSigningIsStillNoticedAtTheReplacedAddress() {
    changeEmailOn(EMAIL_CHANGED_AT);
    var batch = signedWithdrawalBatchPensionikeskusRejects(Set.of());
    given(episService.getContactDetails(any())).willThrow(new IllegalStateException());

    assertThatThrownBy(() -> completeAsOwner(batch)).isInstanceOf(IllegalStateException.class);

    then(emailService)
        .should(times(1))
        .send(any(), argThat(addressedTo(OLD_EMAIL)), eq("previous_contact_activity_en"));
    then(emailService).should(never()).send(any(), any(), eq("withdrawal_batch_en"));
  }

  @Test
  void aFailingLookupOfReplacedEmailsFailsNeitherTheSigningNorTheSubmissionToPensionikeskus() {
    changeEmailOn(EMAIL_CHANGED_AT);
    willThrow(new IllegalStateException()).given(recentlyReplacedEmails).of(any(), any());

    signedWithdrawalBatchPensionikeskusRejects(Set.of());

    then(episService).should(times(2)).sendMandateV2(any());
  }

  private void changeEmailOn(Instant changedAt) {
    ClockHolder.setClock(Clock.fixed(changedAt, UTC));
    changeEmailThroughTheApi(OLD_EMAIL);
    changeEmailThroughTheApi(NEW_EMAIL);
  }

  private void changeEmailThroughTheApi(String email) {
    changeContactDetailsThroughTheApi(Map.of("email", email));
  }

  private void changeContactDetailsThroughTheApi(String email, String phoneNumber) {
    changeContactDetailsThroughTheApi(Map.of("email", email, "phoneNumber", phoneNumber));
  }

  private void changeContactDetailsThroughTheApi(Map<String, String> contactDetails) {
    restTestClient
        .patch()
        .uri("/v1/me")
        .headers(
            headers -> {
              headers.addAll(getHeaders());
              headers.set(ACCEPT_LANGUAGE, "en");
            })
        .body(contactDetails)
        .exchange()
        .expectStatus()
        .isOk();
  }

  private MandateBatch signedWithdrawalBatchPensionikeskusRejects(Set<MandateType> rejected) {
    return signedWithdrawalBatchSubmittedTo(
        command ->
            rejected.contains(command.submission().getMandateType())
                ? rejection(command)
                : acceptance(command));
  }

  private static MandateProcessResult acceptance(MandateSubmissionCommand<?> command) {
    return new MandateProcessResult(
        List.of(new MandateProcessOutcome(command.processId(), true, null)));
  }

  private static MandateProcessResult rejection(MandateSubmissionCommand<?> command) {
    return new MandateProcessResult(
        List.of(new MandateProcessOutcome(command.processId(), false, 40551)));
  }

  private MandateBatch signedWithdrawalBatchSubmittedTo(
      Function<MandateSubmissionCommand<?>, MandateProcessResult> pensionikeskus) {
    given(withdrawalEligibilityService.getWithdrawalEligibility(any()))
        .willReturn(
            WithdrawalEligibilityDto.builder()
                .hasReachedEarlyRetirementAge(true)
                .canWithdrawThirdPillarWithReducedTax(true)
                .age(65)
                .recommendedDurationYears(20)
                .arrestsOrBankruptciesPresent(false)
                .build());
    given(episService.getCashFlowStatement(any(), any(), any()))
        .willReturn(new CashFlowStatement());
    given(episService.getContactDetails(any())).willReturn(contactDetailsFixture());
    given(episService.updateContactDetails(any(), any()))
        .willAnswer(invocation -> invocation.getArgument(1));
    given(episService.sendMandateV2(any()))
        .willAnswer(invocation -> pensionikeskus.apply(invocation.getArgument(0)));
    restTestClient
        .post()
        .uri("/v1/mandate-batches")
        .headers(headers -> headers.addAll(getHeaders()))
        .body(
            MandateBatchDto.builder()
                .mandates(
                    List.of(
                        MandateDto.builder()
                            .details(MandateFixture.aFundPensionOpeningMandateDetails)
                            .build(),
                        MandateDto.builder()
                            .details(MandateFixture.aPartialWithdrawalMandateDetails)
                            .build()))
                .build())
        .exchange()
        .expectStatus()
        .isOk();
    var batch = Streamable.of(mandateBatchRepository.findAll()).toList().getFirst();
    var signer = userRepository.findByPersonalCode(samplePerson().getPersonalCode()).orElseThrow();
    given(signatureService.getSignedFile(any(MobileIdSignatureSession.class), any()))
        .willReturn(new byte[] {1, 2, 3});
    willDoNothing()
        .given(mandateBatchProcessingPoller)
        .startPollingForBatchProcessingFinished(any(), any());

    mandateBatchService.finalizeMobileSignature(
        signer.getId(), batch.getId(), mock(MobileIdSignatureSession.class), ENGLISH);

    return mandateBatchRepository.findById(batch.getId()).orElseThrow();
  }

  private void completeAsOwner(MandateBatch batch) {
    securityContextRunner.runAs(
        batch.getMandates().getFirst().getUser(),
        () -> mandateBatchCompletion.complete(batch, ENGLISH));
  }

  private static ArgumentMatcher<MandrillMessage> addressedTo(String email) {
    return message -> email.equals(message.getTo().getFirst().getEmail());
  }

  private static MandrillMessageStatus acceptedFor(String email) {
    return LutungGsonUtils.getGson()
        .fromJson(
            "{\"_id\": \"accepted-"
                + email
                + "\", \"email\": \""
                + email
                + "\", \"status\": \"sent\"}",
            MandrillMessageStatus.class);
  }

  private void stubEpisAcceptingTheCancellation() {
    given(episService.getCashFlowStatement(any(), any(), any()))
        .willReturn(new CashFlowStatement());
    given(episService.getContactDetails(any())).willReturn(contactDetailsFixture());
    given(episService.updateContactDetails(any(), any()))
        .willAnswer(invocation -> invocation.getArgument(1));
    given(episService.sendMandateV2(any()))
        .willAnswer(
            invocation -> {
              MandateSubmissionCommand<?> command = invocation.getArgument(0);
              return new MandateProcessResult(
                  List.of(new MandateProcessOutcome(command.processId(), true, null)));
            });
  }

  private Long draftRejoinCancellation() {
    return restTestClient
        .post()
        .uri("/v1/applications/321/cancellations")
        .headers(headers -> headers.addAll(getHeaders()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(ApplicationCancellationResponse.class)
        .returnResult()
        .getResponseBody()
        .getMandateId();
  }

  private void signWithMobileId(Long mandateId) {
    var signer = userRepository.findByPersonalCode(samplePerson().getPersonalCode()).orElseThrow();
    userRepository.save(withContactDetails(signer, "rejoiner@example.com", "+37255555555"));
    given(signatureService.getSignedFile(any(MobileIdSignatureSession.class), any()))
        .willReturn(new byte[] {1, 2, 3});

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            transaction ->
                mandateService.finalizeMobileIdSignature(
                    signer.getId(),
                    mandateId,
                    mock(MobileIdSignatureSession.class),
                    Locale.ENGLISH));
  }

  private void recordAcceptedByEpis(Mandate mandate) {
    mandateProcessRepository.save(
        MandateProcess.builder()
            .mandate(mandate)
            .processId("accepted" + mandate.getId())
            .type(ApplicationType.FUND_PENSION_OPENING)
            .successful(true)
            .build());
  }

  private static ApplicationSnapshot pendingRejoinFiledOn(Instant date) {
    return rejoinFiledOn(date, ApplicationStatus.PENDING);
  }

  private static ApplicationSnapshot rejoinFiledOn(Instant date, ApplicationStatus status) {
    return ApplicationSnapshot.builder()
        .id(321L)
        .date(date)
        .documentNumber("654")
        .status(status)
        .type(ApplicationType.REJOIN)
        .fundTransferExchanges(List.of())
        .build();
  }

  private static String authenticatedPersonalCode() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getCredentials() instanceof String)) {
      return "no authentication with a token";
    }
    return ((AuthenticatedPerson) authentication.getPrincipal()).getPersonalCode();
  }
}
