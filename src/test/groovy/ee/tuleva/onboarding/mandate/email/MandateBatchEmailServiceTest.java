package ee.tuleva.onboarding.mandate.email;

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser;
import static ee.tuleva.onboarding.conversion.ConversionResponseFixture.notConverted;
import static ee.tuleva.onboarding.epis.contact.ContactDetailsFixture.contactDetailsFixture;
import static ee.tuleva.onboarding.mandate.MandateFixture.*;
import static ee.tuleva.onboarding.mandate.email.EmailVariablesAttachments.getAttachments;
import static ee.tuleva.onboarding.paymentrate.PaymentRatesFixture.samplePaymentRates;
import static org.mockito.Mockito.*;

import com.microtripit.mandrillapp.lutung.view.MandrillMessage;
import com.microtripit.mandrillapp.lutung.view.MandrillMessageStatus;
import ee.tuleva.onboarding.error.response.ErrorsResponse;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.batch.MandateBatchFixture;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
import ee.tuleva.onboarding.mandate.processor.MandateProcessorService;
import ee.tuleva.onboarding.notification.email.EmailService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MandateBatchEmailServiceTest {

  @Mock private EmailService emailService;

  @Mock private EmailPersistenceService emailPersistenceService;

  @Mock private MandateProcessorService mandateProcessorService;

  @InjectMocks private MandateBatchEmailService mandateBatchEmailService;

  private boolean areMergeVarsPresent(Map<String, Object> first, Map<String, Object> second) {
    return first.entrySet().stream().allMatch(e -> e.getValue().equals(second.get(e.getKey())));
  }

  @Test
  @DisplayName("sends email for withdrawal mandate batch")
  void withdrawalMandateEmail() {
    Mandate mandate1 = sampleFundPensionOpeningMandate();
    Mandate mandate2 = samplePartialWithdrawalMandate();

    MandateBatch mandateBatch = MandateBatchFixture.aSavedMandateBatch(List.of(mandate1, mandate2));

    mandateBatch.setFile("1234".getBytes());

    var user = sampleUser().build();
    var conversion = notConverted();
    var contactDetails = contactDetailsFixture();
    var paymentRates = samplePaymentRates();
    var pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates);
    var message = new MandrillMessage();

    var file = getAttachments(user, mandateBatch).getFirst();

    var mandrillResponse = mock(MandrillMessageStatus.class);
    when(mandrillResponse.getId()).thenReturn("123");
    when(mandrillResponse.getStatus()).thenReturn("sent");

    Map<String, Object> mergeVars =
        Map.ofEntries(
            Map.entry("fname", user.getFirstName()),
            Map.entry("lname", user.getLastName()),
            Map.entry("transferDate", "03.05.2021"),
            Map.entry("suggestPaymentRate", pillarSuggestion.isSuggestPaymentRate()),
            Map.entry("suggestSecondPillar", pillarSuggestion.isSuggestSecondPillar()),
            Map.entry("suggestThirdPillar", pillarSuggestion.isSuggestThirdPillar()),
            Map.entry("suggestMembership", pillarSuggestion.isSuggestMembership()),
            Map.entry("fundPensionSecondPillar", true),
            Map.entry("fundPensionThirdPillar", false),
            Map.entry("partialWithdrawalSecondPillar", true),
            Map.entry("partialWithdrawalThirdPillar", false));

    var tags = List.of("mandate_batch", "pillar_2", "fund_pension_opening", "partial_withdrawal");

    when(emailPersistenceService.hasEmailsFor(mandateBatch)).thenReturn(false);
    when(emailService.send(user, message, "withdrawal_batch_en"))
        .thenReturn(Optional.of(mandrillResponse));
    when(emailService.newMandrillMessage(
            eq(user.getEmail()),
            eq("withdrawal_batch_en"),
            argThat(map -> areMergeVarsPresent(map, mergeVars)),
            eq(tags),
            argThat(
                attachments ->
                    attachments.size() == 1
                        && attachments.getFirst().getName().equals(file.getName())
                        && attachments.getFirst().getContent().equals(file.getContent()))))
        .thenReturn(message);

    mandateBatchEmailService.sendMandateBatch(user, mandateBatch, pillarSuggestion, Locale.ENGLISH);

    verify(emailPersistenceService)
        .save(user, "123", EmailType.WITHDRAWAL_BATCH, "sent", mandateBatch);
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
    var conversion = notConverted();
    var contactDetails = contactDetailsFixture();
    var paymentRates = samplePaymentRates();
    var pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates);
    var message = new MandrillMessage();

    var file = getAttachments(user, mandateBatch).getFirst();

    var mandrillResponse = mock(MandrillMessageStatus.class);
    when(mandrillResponse.getId()).thenReturn("123");
    when(mandrillResponse.getStatus()).thenReturn("sent");

    Map<String, Object> mergeVars =
        Map.ofEntries(
            Map.entry("fname", user.getFirstName()),
            Map.entry("lname", user.getLastName()),
            Map.entry("transferDate", "03.05.2021"),
            Map.entry("suggestPaymentRate", pillarSuggestion.isSuggestPaymentRate()),
            Map.entry("suggestSecondPillar", pillarSuggestion.isSuggestSecondPillar()),
            Map.entry("suggestThirdPillar", pillarSuggestion.isSuggestThirdPillar()),
            Map.entry("suggestMembership", pillarSuggestion.isSuggestMembership()),
            Map.entry("fundPensionSecondPillar", true),
            Map.entry("fundPensionThirdPillar", true),
            Map.entry("partialWithdrawalSecondPillar", true),
            Map.entry("partialWithdrawalThirdPillar", true));

    var tags =
        List.of(
            "mandate_batch", "pillar_2", "pillar_3", "fund_pension_opening", "partial_withdrawal");

    when(emailPersistenceService.hasEmailsFor(mandateBatch)).thenReturn(false);
    when(emailService.send(user, message, "withdrawal_batch_en"))
        .thenReturn(Optional.of(mandrillResponse));
    when(emailService.newMandrillMessage(
            eq(user.getEmail()),
            eq("withdrawal_batch_en"),
            argThat(map -> areMergeVarsPresent(map, mergeVars)),
            eq(tags),
            argThat(
                attachments ->
                    attachments.size() == 1
                        && attachments.getFirst().getName().equals(file.getName())
                        && attachments.getFirst().getContent().equals(file.getContent()))))
        .thenReturn(message);

    mandateBatchEmailService.sendMandateBatch(user, mandateBatch, pillarSuggestion, Locale.ENGLISH);

    verify(emailPersistenceService)
        .save(user, "123", EmailType.WITHDRAWAL_BATCH, "sent", mandateBatch);
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
            Map.entry("transferDate", "03.05.2021"),
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

    when(emailPersistenceService.hasEmailsFor(mandateBatch)).thenReturn(false);
    when(emailService.send(user, message, "batch_failed_en"))
        .thenReturn(Optional.of(mandrillResponse));

    when(emailService.newMandrillMessage(
            eq(user.getEmail()),
            eq("batch_failed_en"),
            argThat(map -> areMergeVarsPresent(map, mergeVars)),
            eq(tags),
            argThat(
                attachments ->
                    attachments.size() == 1
                        && attachments.getFirst().getName().equals(file.getName())
                        && attachments.getFirst().getContent().equals(file.getContent()))))
        .thenReturn(message);

    mandateBatchEmailService.sendMandateBatchFailedEmail(user, mandateBatch, Locale.ENGLISH);

    verify(emailPersistenceService).save(user, "123", EmailType.BATCH_FAILED, "sent", mandateBatch);
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
    var conversion = notConverted();
    var contactDetails = contactDetailsFixture();
    var paymentRates = samplePaymentRates();
    var pillarSuggestion = new PillarSuggestion(user, contactDetails, conversion, paymentRates);

    when(emailPersistenceService.hasEmailsFor(mandateBatch)).thenReturn(true);

    mandateBatchEmailService.sendMandateBatch(user, mandateBatch, pillarSuggestion, Locale.ENGLISH);

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
    var conversion = notConverted();
    var contactDetails = contactDetailsFixture();
    var paymentRates = samplePaymentRates();

    when(emailPersistenceService.hasEmailsFor(mandateBatch)).thenReturn(true);

    mandateBatchEmailService.sendMandateBatchFailedEmail(user, mandateBatch, Locale.ENGLISH);

    verify(emailService, times(0)).send(any(), any(), any());
    verify(emailPersistenceService, times(0)).save(any(), any(), any(), any());
  }
}
