package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.mandate.EmailVariablesAttachments.getAttachments;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static ee.tuleva.onboarding.mandate.batch.MandateBatchStatus.SIGNED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.deadline.MandateDeadlinesService;
import ee.tuleva.onboarding.deadline.PublicHolidays;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.batch.MandateBatchFixture;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.notification.email.EmailPersistenceService;
import ee.tuleva.onboarding.notification.email.EmailService;
import ee.tuleva.onboarding.notification.email.EmailType;
import ee.tuleva.onboarding.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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
            new MandateDeadlinesService(estonianClock, new PublicHolidays()));
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
            eq(rejoinMergeVars(user, "1. mai 2027", "30. november 2026", false, false)),
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
            eq(rejoinMergeVars(user, "September 1, 2027", "March 31, 2027", false, false)),
            eq(List.of("mandate_batch")),
            any());
  }

  @Test
  void rejoinWithAFundSelectionListsTheSelectionApplicationAmongTheFiledOnes() {
    var rejoin = sampleRejoinMandate();
    rejoin.setCreatedDate(Instant.parse("2026-11-30T21:59:59Z"));
    var mandateBatch = aSignedBatch(rejoin, sampleSelectionMandate());
    var user = sampleUser().build();

    mandateBatchEmailService.sendMandateBatch(user, mandateBatch, Locale.of("et"));

    then(emailService)
        .should()
        .newMandrillMessage(
            eq(user.getEmail()),
            eq("second_pillar_rejoin_et"),
            eq(rejoinMergeVars(user, "1. mai 2027", "30. november 2026", false, true)),
            eq(List.of("mandate_batch")),
            any());
  }

  @Test
  void rejoinWithAPaymentRateChangeListsTheRateApplicationAmongTheFiledOnes() {
    var rejoin = sampleRejoinMandate();
    rejoin.setCreatedDate(Instant.parse("2026-11-30T21:59:59Z"));
    var mandateBatch = aSignedBatch(rejoin, sampleMandateWithPaymentRate());
    var user = sampleUser().build();

    mandateBatchEmailService.sendMandateBatch(user, mandateBatch, Locale.of("et"));

    then(emailService)
        .should()
        .newMandrillMessage(
            eq(user.getEmail()),
            eq("second_pillar_rejoin_et"),
            eq(rejoinMergeVars(user, "1. mai 2027", "30. november 2026", true, false)),
            eq(List.of("mandate_batch")),
            any());
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
      boolean paymentRateChange,
      boolean fundSelection) {
    return Map.ofEntries(
        Map.entry("fname", user.getFirstName()),
        Map.entry("lname", user.getLastName()),
        Map.entry("fundPensionSecondPillar", false),
        Map.entry("fundPensionThirdPillar", false),
        Map.entry("partialWithdrawalSecondPillar", false),
        Map.entry("partialWithdrawalThirdPillar", false),
        Map.entry("contributionStartDate", contributionStartDate),
        Map.entry("cancellationDeadline", cancellationDeadline),
        Map.entry("paymentRateChange", paymentRateChange),
        Map.entry("fundSelection", fundSelection));
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
