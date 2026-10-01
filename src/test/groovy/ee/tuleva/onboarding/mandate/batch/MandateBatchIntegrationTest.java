package ee.tuleva.onboarding.mandate.batch;

import static ee.tuleva.onboarding.auth.JwtTokenGenerator.getHeaders;
import static ee.tuleva.onboarding.auth.PersonFixture.samplePerson;
import static ee.tuleva.onboarding.epis.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.mandate.MandateType.FUND_PENSION_OPENING;
import static ee.tuleva.onboarding.mandate.MandateType.PARTIAL_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.INITIALIZED;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.SIGNED;
import static ee.tuleva.onboarding.notification.OperationsNotificationService.Channel.WITHDRAWALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import ee.tuleva.onboarding.aml.AmlAutoChecker;
import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.CashFlowStatement;
import ee.tuleva.onboarding.epis.ContactDetails;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.mandate.MandateFixture;
import ee.tuleva.onboarding.mandate.MandateRepository;
import ee.tuleva.onboarding.mandate.batch.poller.MandateBatchProcessingPoller;
import ee.tuleva.onboarding.mandate.email.MandateBatchEmailService;
import ee.tuleva.onboarding.mandate.generic.MandateDto;
import ee.tuleva.onboarding.notification.OperationsNotificationService;
import ee.tuleva.onboarding.user.UserRepository;
import ee.tuleva.onboarding.withdrawals.WithdrawalEligibilityDto;
import ee.tuleva.onboarding.withdrawals.WithdrawalEligibilityService;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.util.Streamable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureRestTestClient
class MandateBatchIntegrationTest {

  @Autowired private RestTestClient restTestClient;

  @Autowired private JsonMapper mapper;

  @Autowired private MandateBatchRepository mandateBatchRepository;
  @Autowired private MandateRepository mandateRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private MandateBatchProcessingPoller mandateBatchProcessingPoller;

  @MockitoBean private EpisService episService;
  @MockitoBean private AmlAutoChecker amlAutoChecker;
  @MockitoBean private WithdrawalEligibilityService withdrawalEligibilityService;
  @MockitoBean private OperationsNotificationService notificationService;
  @MockitoSpyBean private MandateBatchEmailService mandateBatchEmailService;

  // The JWT authenticates samplePerson, and the auth filter creates that user on the server
  // thread, which no test-side transaction can roll back. It outlives the test in the shared
  // database and collides with every later test that inserts the same personal code.
  @AfterEach
  void cleanup() {
    mandateRepository.deleteAll();
    mandateBatchRepository.deleteAll();
    userRepository
        .findByPersonalCode(samplePerson().getPersonalCode())
        .ifPresent(userRepository::delete);
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
    var personalCodesEpisWasCalledAs = givenAWithdrawalEligibleOwnerKnownToEpis();
    var batch = aSignedBatch();

    mandateBatchProcessingPoller.startPollingForBatchProcessingFinished(batch, Locale.ENGLISH);
    mandateBatchProcessingPoller.processQueue();

    var ownerPersonalCode = samplePerson().getPersonalCode();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                then(mandateBatchEmailService)
                    .should()
                    .sendMandateBatch(
                        argThat(user -> user.getPersonalCode().equals(ownerPersonalCode)),
                        argThat(sentBatch -> sentBatch.getId().equals(batch.getId())),
                        eq(Locale.ENGLISH)));
    assertThat(personalCodesEpisWasCalledAs).containsExactly(ownerPersonalCode, ownerPersonalCode);
  }

  @Test
  void ownersStatusPollCompletesASignedBatchWhosePollerWasDroppedAndDoesSoOnlyOnce() {
    var personalCodesEpisWasCalledAs = givenAWithdrawalEligibleOwnerKnownToEpis();
    var batch = aSignedBatch();

    pollIdCardSignatureStatusAndExpectSignature(batch);
    pollIdCardSignatureStatusAndExpectSignature(batch);

    var ownerPersonalCode = samplePerson().getPersonalCode();
    then(mandateBatchEmailService)
        .should(times(1))
        .sendMandateBatch(
            argThat(user -> user.getPersonalCode().equals(ownerPersonalCode)),
            argThat(sentBatch -> sentBatch.getId().equals(batch.getId())),
            any());
    assertThat(personalCodesEpisWasCalledAs).containsExactly(ownerPersonalCode, ownerPersonalCode);
  }

  @Test
  void aBatchThePollerCompletedIsNotCompletedAgainByTheOwnersStatusPoll() {
    var personalCodesEpisWasCalledAs = givenAWithdrawalEligibleOwnerKnownToEpis();
    var batch = aSignedBatch();
    mandateBatchProcessingPoller.startPollingForBatchProcessingFinished(batch, Locale.ENGLISH);
    mandateBatchProcessingPoller.processQueue();
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> then(mandateBatchEmailService).should().sendMandateBatch(any(), any(), any()));

    pollIdCardSignatureStatusAndExpectSignature(batch);

    then(mandateBatchEmailService).should(times(1)).sendMandateBatch(any(), any(), any());
    assertThat(personalCodesEpisWasCalledAs).hasSize(2);
  }

  private List<String> givenAWithdrawalEligibleOwnerKnownToEpis() {
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
    return personalCodesEpisWasCalledAs;
  }

  private MandateBatch aSignedBatch() {
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
    batch.setStatus(SIGNED);
    batch.setFile("signed container".getBytes());
    return mandateBatchRepository.save(batch);
  }

  private void pollIdCardSignatureStatusAndExpectSignature(MandateBatch batch) {
    restTestClient
        .get()
        .uri("/v1/mandate-batches/" + batch.getId() + "/signature/id-card/status")
        .headers(h -> h.addAll(getHeaders()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.statusCode")
        .isEqualTo("SIGNATURE");
  }

  private static String authenticatedPersonalCode() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getCredentials() instanceof String)) {
      return "no authentication with a token";
    }
    return ((AuthenticatedPerson) authentication.getPrincipal()).getPersonalCode();
  }
}
