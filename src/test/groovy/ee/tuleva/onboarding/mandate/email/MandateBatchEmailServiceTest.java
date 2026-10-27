package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.fund.FundFixture.lhv2ndPillarFund;
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarBondFund;
import static ee.tuleva.onboarding.fund.FundFixture.tuleva2ndPillarStockFund;
import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.getAttachments;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.SIGNED;
import static ee.tuleva.onboarding.mandate.details.PaymentRateChangeMandateDetails.PaymentRate.FOUR;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.batch.MandateBatchFixture;
import ee.tuleva.onboarding.mandate.details.PaymentRateChangeMandateDetails;
import ee.tuleva.onboarding.mandate.details.SelectionMandateDetails;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.nudge.PensionRegistry;
import ee.tuleva.onboarding.nudge.PensionRegistrySnapshot;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MandateBatchEmailServiceTest {

  @Mock private EmailService emailService;

  @Mock private EmailPersistenceService emailPersistenceService;

  @Mock private MandateProcessorService mandateProcessorService;

  @Mock private FundRepository fundRepository;

  @Mock private PensionRegistry pensionRegistry;

  private MandateBatchEmailService mandateBatchEmailService;

  @BeforeEach
  void setUp() {
    var estonianClock =
        Clock.fixed(Instant.parse("2026-10-22T09:00:00Z"), ZoneId.of("Europe/Tallinn"));
    mandateBatchEmailService =
        new MandateBatchEmailService(
            emailService,
            emailPersistenceService,
            mandateProcessorService,
            new MandateDeadlinesService(estonianClock, new PublicHolidays()),
            fundRepository,
            pensionRegistry);
  }

  @Test
  void
      rejoinDatedByTheEndOfThirtiethNovemberTellsInEstonianItCanBeCancelledThatDayAndContributionsStartInMay() {
    var rejoin = sampleRejoinMandate();
    rejoin.setCreatedDate(Instant.parse("2026-11-30T21:59:59Z"));
    var mandateBatch = aSignedBatch(rejoin);
    var user = sampleUser().build();

    mandateBatchEmailService.sendMandateBatch(user, mandateBatch, Locale.of("et"));

    then(emailService)
        .should()
        .newMandrillMessage(
            eq(user.getEmail()),
            eq("second_pillar_rejoin_et"),
            eq(rejoinMergeVars(user, "1. mai 2027", "30. november 2026", nothingChanged())),
            eq(List.of("mandate_batch")),
            any());
  }

  @Test
  void
      rejoinDatedOnFirstDecemberTellsInEnglishItCanBeCancelledUntilMarchAndContributionsStartInSeptember() {
    var rejoin = sampleRejoinMandate();
    rejoin.setCreatedDate(Instant.parse("2026-11-30T22:00:30Z"));
    var mandateBatch = aSignedBatch(rejoin);
    var user = sampleUser().build();

    mandateBatchEmailService.sendMandateBatch(user, mandateBatch, Locale.ENGLISH);

    then(emailService)
        .should()
        .newMandrillMessage(
            eq(user.getEmail()),
            eq("second_pillar_rejoin_en"),
            eq(rejoinMergeVars(user, "September 1, 2027", "March 31, 2027", nothingChanged())),
            eq(List.of("mandate_batch")),
            any());
  }

  @Test
  void rejoinSelectingTheTulevaStockFundNamesItAsWhereContributionsGo() {
    var stockFund = tuleva2ndPillarStockFund();
    given(fundRepository.findByIsin(stockFund.getIsin())).willReturn(stockFund);

    sendEstonianRejoin(sampleRejoinMandate(), selectionOf(stockFund));

    shouldSendEstonianRejoin(with(fundSelected(), Map.of("selectedTulevaStockFund", true)));
  }

  @Test
  void rejoinSelectingTheTulevaBondFundNamesItAsWhereContributionsGo() {
    var bondFund = tuleva2ndPillarBondFund();
    given(fundRepository.findByIsin(bondFund.getIsin())).willReturn(bondFund);

    sendEstonianRejoin(sampleRejoinMandate(), selectionOf(bondFund));

    shouldSendEstonianRejoin(with(fundSelected(), Map.of("selectedTulevaBondFund", true)));
  }

  @Test
  void rejoinSelectingAnotherManagersHighFeeFundNamesItAndItsFee() {
    var lhvFund = lhv2ndPillarFund();
    given(fundRepository.findByIsin(lhvFund.getIsin())).willReturn(lhvFund);

    sendEstonianRejoin(sampleRejoinMandate(), selectionOf(lhvFund));

    shouldSendEstonianRejoin(
        with(
            fundSelected(),
            Map.of(
                "selectedOtherFund",
                true,
                "selectedFundName",
                "LHV XL",
                "selectedHighFeeFund",
                true,
                "selectedFundFee",
                "1")));
  }

  @Test
  void rejoinSelectingAnotherManagersLowFeeFundNamesItWithoutAFee() {
    var lowFeeFund = lhv2ndPillarFund();
    lowFeeFund.setOngoingChargesFigure(new BigDecimal("0.0029"));
    given(fundRepository.findByIsin(lowFeeFund.getIsin())).willReturn(lowFeeFund);

    sendEstonianRejoin(sampleRejoinMandate(), selectionOf(lowFeeFund));

    shouldSendEstonianRejoin(
        with(fundSelected(), Map.of("selectedOtherFund", true, "selectedFundName", "LHV XL")));
  }

  @Test
  void rejoinKeepingATulevaFundOnFileSaysContributionsContinueThere() {
    var user = sampleUser().build();
    given(pensionRegistry.snapshotFor(user.getPersonalCode()))
        .willReturn(Optional.of(new PensionRegistrySnapshot(false, true, true, true, true, true)));

    sendEstonianRejoin(sampleRejoinMandate());

    shouldSendEstonianRejoin(with(nothingChanged(), Map.of("keptTulevaFund", true)));
  }

  @Test
  void rejoinHoldingTulevaUnitsButContributingElsewhereSaysContributionsGoToTheOldFund() {
    var user = sampleUser().build();
    given(pensionRegistry.snapshotFor(user.getPersonalCode()))
        .willReturn(Optional.of(new PensionRegistrySnapshot(false, true, true, true, true, false)));

    sendEstonianRejoin(sampleRejoinMandate());

    shouldSendEstonianRejoin(nothingChanged());
  }

  @Test
  void rejoinSelectingAFundMissingFromTheFundListFailsInsteadOfNamingTheOldFund() {
    var selection = selectionOf(lhv2ndPillarFund());

    assertThatThrownBy(() -> sendEstonianRejoin(sampleRejoinMandate(), selection))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejoinRaisingTheRateToSixPercentSaysTheTaxBenefitIsUsedToTheMaximum() {
    sendEstonianRejoin(sampleRejoinMandate(), sampleMandateWithPaymentRate());

    shouldSendEstonianRejoin(
        with(
            nothingChanged(),
            Map.of("paymentRateChange", true, "paymentRate", 6, "maxPaymentRate", true)));
  }

  @Test
  void rejoinRaisingTheRateToFourPercentNamesTheRate() {
    sendEstonianRejoin(
        sampleRejoinMandate(),
        sampleMandateWithPaymentRate(new PaymentRateChangeMandateDetails(FOUR)));

    shouldSendEstonianRejoin(
        with(nothingChanged(), Map.of("paymentRateChange", true, "paymentRate", 4)));
  }

  private void sendEstonianRejoin(Mandate rejoin, Mandate... alongside) {
    rejoin.setCreatedDate(Instant.parse("2026-11-30T21:59:59Z"));
    var mandates = new ArrayList<Mandate>(List.of(rejoin));
    mandates.addAll(List.of(alongside));
    mandateBatchEmailService.sendMandateBatch(
        sampleUser().build(), aSignedBatch(mandates.toArray(Mandate[]::new)), Locale.of("et"));
  }

  private void shouldSendEstonianRejoin(Map<String, Object> rejoinChoices) {
    var user = sampleUser().build();
    then(emailService)
        .should()
        .newMandrillMessage(
            eq(user.getEmail()),
            eq("second_pillar_rejoin_et"),
            eq(rejoinMergeVars(user, "1. mai 2027", "30. november 2026", rejoinChoices)),
            eq(List.of("mandate_batch")),
            any());
  }

  private static Mandate selectionOf(Fund fund) {
    var selection = sampleSelectionMandate();
    selection.setFutureContributionFundIsin(fund.getIsin());
    selection.setDetails(new SelectionMandateDetails(fund.getIsin()));
    return selection;
  }

  private static Map<String, Object> nothingChanged() {
    return Map.ofEntries(
        Map.entry("paymentRateChange", false),
        Map.entry("fundSelection", false),
        Map.entry("selectedTulevaStockFund", false),
        Map.entry("selectedTulevaBondFund", false),
        Map.entry("selectedOtherFund", false),
        Map.entry("selectedHighFeeFund", false),
        Map.entry("keptTulevaFund", false),
        Map.entry("paymentRate", 2),
        Map.entry("maxPaymentRate", false));
  }

  private static Map<String, Object> fundSelected() {
    return with(nothingChanged(), Map.of("fundSelection", true));
  }

  private static Map<String, Object> with(Map<String, Object> base, Map<String, Object> overrides) {
    var merged = new HashMap<>(base);
    merged.putAll(overrides);
    return merged;
  }

  private static MandateBatch aSignedBatch(Mandate... mandates) {
    var mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandates), SIGNED);
    mandateBatch.setFile("1234".getBytes());
    return mandateBatch;
  }

  private static Map<String, Object> rejoinMergeVars(
      User user,
      String contributionStartDate,
      String cancellationDeadline,
      Map<String, Object> rejoinChoices) {
    var mergeVars =
        new HashMap<String, Object>(
            Map.ofEntries(
                Map.entry("fname", user.getFirstName()),
                Map.entry("lname", user.getLastName()),
                Map.entry("fundPensionSecondPillar", false),
                Map.entry("fundPensionThirdPillar", false),
                Map.entry("partialWithdrawalSecondPillar", false),
                Map.entry("partialWithdrawalThirdPillar", false),
                Map.entry("contributionStartDate", contributionStartDate),
                Map.entry("cancellationDeadline", cancellationDeadline)));
    mergeVars.putAll(rejoinChoices);
    return mergeVars;
  }

  @Test
  @DisplayName("sends email for withdrawal mandate batch")
  void withdrawalMandateEmail() {
    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();

    MandateBatch mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

    mandateBatch.setFile("1234".getBytes());

    var user = sampleUser().build();
    var message = new MandrillMessage();

    var file = getAttachments(user, mandateBatch).getFirst();

    var mandrillResponse = mock(MandrillMessageStatus.class);
    when(mandrillResponse.getId()).thenReturn("123");
    when(mandrillResponse.getStatus()).thenReturn("sent");

    Map<String, Object> mergeVars =
        Map.ofEntries(
            Map.entry("fname", user.getFirstName()),
            Map.entry("lname", user.getLastName()),
            Map.entry("fundPensionSecondPillar", true),
            Map.entry("fundPensionThirdPillar", false),
            Map.entry("partialWithdrawalSecondPillar", true),
            Map.entry("partialWithdrawalThirdPillar", false));

    var tags =
        new java.util.ArrayList<>(
            List.of("mandate_batch", "pillar_2", "fund_pension_opening", "partial_withdrawal"));

    when(emailPersistenceService.hasEmailsForMandateBatch(mandateBatch.getId())).thenReturn(false);
    when(emailService.send(user, message, "withdrawal_batch_en"))
        .thenReturn(Optional.of(mandrillResponse));
    when(emailService.newMandrillMessage(
            eq(user.getEmail()),
            eq("withdrawal_batch_en"),
            eq(mergeVars),
            eq(tags),
            argThat(
                attachments ->
                    attachments.size() == 1
                        && attachments.getFirst().getName().equals(file.getName())
                        && attachments.getFirst().getContent().equals(file.getContent()))))
        .thenReturn(message);

    mandateBatchEmailService.sendMandateBatch(user, mandateBatch, Locale.ENGLISH);

    verify(emailPersistenceService)
        .saveWithMandateBatch(
            user, "123", EmailType.WITHDRAWAL_BATCH, "sent", mandateBatch.getId());
  }

  @Test
  @DisplayName("sends email for withdrawal mandate batch with all four types")
  void withdrawalMandateEmailAllTypes() {
    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();
    Mandate mandate3 =
        sampleFundPensionOpeningMandate(aThirdPillarFundPensionOpeningMandateDetails);
    Mandate mandate4 = samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails);

    MandateBatch mandateBatch =
        MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2, mandate3, mandate4));

    mandateBatch.setFile("1234".getBytes());

    var user = sampleUser().build();
    var message = new MandrillMessage();

    var file = getAttachments(user, mandateBatch).getFirst();

    var mandrillResponse = mock(MandrillMessageStatus.class);
    when(mandrillResponse.getId()).thenReturn("123");
    when(mandrillResponse.getStatus()).thenReturn("sent");

    Map<String, Object> mergeVars =
        Map.ofEntries(
            Map.entry("fname", user.getFirstName()),
            Map.entry("lname", user.getLastName()),
            Map.entry("fundPensionSecondPillar", true),
            Map.entry("fundPensionThirdPillar", true),
            Map.entry("partialWithdrawalSecondPillar", true),
            Map.entry("partialWithdrawalThirdPillar", true));

    var tags =
        new java.util.ArrayList<>(
            List.of(
                "mandate_batch",
                "pillar_2",
                "pillar_3",
                "fund_pension_opening",
                "partial_withdrawal"));

    when(emailPersistenceService.hasEmailsForMandateBatch(mandateBatch.getId())).thenReturn(false);
    when(emailService.send(user, message, "withdrawal_batch_en"))
        .thenReturn(Optional.of(mandrillResponse));
    when(emailService.newMandrillMessage(
            eq(user.getEmail()),
            eq("withdrawal_batch_en"),
            eq(mergeVars),
            eq(tags),
            argThat(
                attachments ->
                    attachments.size() == 1
                        && attachments.getFirst().getName().equals(file.getName())
                        && attachments.getFirst().getContent().equals(file.getContent()))))
        .thenReturn(message);

    mandateBatchEmailService.sendMandateBatch(user, mandateBatch, Locale.ENGLISH);

    verify(emailPersistenceService)
        .saveWithMandateBatch(
            user, "123", EmailType.WITHDRAWAL_BATCH, "sent", mandateBatch.getId());
  }

  @Test
  @DisplayName("sends email for failed batch")
  void failedBatchEmail() {
    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();
    Mandate mandate3 =
        sampleFundPensionOpeningMandate(aThirdPillarFundPensionOpeningMandateDetails);
    Mandate mandate4 = samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails);

    MandateBatch mandateBatch =
        MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2, mandate3, mandate4));

    mandateBatch.setFile("1234".getBytes());

    var user = sampleUser().build();
    var message = new MandrillMessage();

    var file = getAttachments(user, mandateBatch).getFirst();

    var mandrillResponse = mock(MandrillMessageStatus.class);
    when(mandrillResponse.getId()).thenReturn("123");
    when(mandrillResponse.getStatus()).thenReturn("sent");

    Map<String, Object> mergeVars =
        Map.ofEntries(
            Map.entry("fname", user.getFirstName()),
            Map.entry("lname", user.getLastName()),
            Map.entry("failedMandateCount", 1),
            Map.entry("successfulMandateCount", 3),
            Map.entry("totalMandateCount", 4),
            Map.entry("fundPensionSecondPillar", true),
            Map.entry("fundPensionThirdPillar", true),
            Map.entry("partialWithdrawalSecondPillar", true),
            Map.entry("partialWithdrawalThirdPillar", true));

    var tags =
        List.of(
            "mandate_batch", "pillar_2", "pillar_3", "fund_pension_opening", "partial_withdrawal");

    when(mandateProcessorService.getErrors(eq(mandate1))).thenReturn(new ErrorsResponse(List.of()));
    when(mandateProcessorService.getErrors(eq(mandate2))).thenReturn(new ErrorsResponse(List.of()));
    when(mandateProcessorService.getErrors(eq(mandate3))).thenReturn(new ErrorsResponse(List.of()));
    when(mandateProcessorService.getErrors(eq(mandate4)))
        .thenReturn(ErrorsResponse.ofSingleError("123", "Error"));

    when(emailPersistenceService.hasEmailsForMandateBatch(mandateBatch.getId())).thenReturn(false);
    when(emailService.send(user, message, "batch_failed_en"))
        .thenReturn(Optional.of(mandrillResponse));

    when(emailService.newMandrillMessage(
            eq(user.getEmail()),
            eq("batch_failed_en"),
            eq(mergeVars),
            eq(tags),
            argThat(
                attachments ->
                    attachments.size() == 1
                        && attachments.getFirst().getName().equals(file.getName())
                        && attachments.getFirst().getContent().equals(file.getContent()))))
        .thenReturn(message);

    mandateBatchEmailService.sendMandateBatchFailedEmail(user, mandateBatch, Locale.ENGLISH);

    verify(emailPersistenceService)
        .saveWithMandateBatch(user, "123", EmailType.BATCH_FAILED, "sent", mandateBatch.getId());
  }

  @Test
  @DisplayName("does not send email if already present for batch")
  void dontSendWhenAlreadySent() {
    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();
    Mandate mandate3 =
        sampleFundPensionOpeningMandate(aThirdPillarFundPensionOpeningMandateDetails);
    Mandate mandate4 = samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails);

    MandateBatch mandateBatch =
        MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2, mandate3, mandate4));

    mandateBatch.setFile("1234".getBytes());

    var user = sampleUser().build();

    when(emailPersistenceService.hasEmailsForMandateBatch(mandateBatch.getId())).thenReturn(true);

    mandateBatchEmailService.sendMandateBatch(user, mandateBatch, Locale.ENGLISH);

    verify(emailService, times(0)).send(any(), any(), any());
    verify(emailPersistenceService, times(0)).save(any(), any(), any(), any());
  }

  @Test
  @DisplayName("does not send failed email if already present for batch")
  void dontSendFailedWhenAlreadySent() {
    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();
    Mandate mandate3 =
        sampleFundPensionOpeningMandate(aThirdPillarFundPensionOpeningMandateDetails);
    Mandate mandate4 = samplePartialWithdrawalMandate(aThirdPillarPartialWithdrawalMandateDetails);

    MandateBatch mandateBatch =
        MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2, mandate3, mandate4));

    mandateBatch.setFile("1234".getBytes());

    var user = sampleUser().build();

    when(emailPersistenceService.hasEmailsForMandateBatch(mandateBatch.getId())).thenReturn(true);

    mandateBatchEmailService.sendMandateBatchFailedEmail(user, mandateBatch, Locale.ENGLISH);

    verify(emailService, times(0)).send(any(), any(), any());
    verify(emailPersistenceService, times(0)).save(any(), any(), any(), any());
  }
}
