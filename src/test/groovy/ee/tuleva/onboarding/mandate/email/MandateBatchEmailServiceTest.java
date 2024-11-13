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
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.mandate.batch.MandateBatchFixture;
import ee.tuleva.onboarding.mandate.email.persistence.EmailPersistenceService;
import ee.tuleva.onboarding.mandate.email.persistence.EmailType;
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

  @InjectMocks private MandateBatchEmailService mandateBatchEmailService;

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

    var tags =
        List.of(
            "mandate_batch",
            "pillar_2",
            "fund_pension_opening",
            "partial_withdrawal",
            "suggest_payment_rate",
            "suggest_2",
            "suggest_3");

    when(emailService.send(user, message, "withdrawal_batch_en"))
        .thenReturn(Optional.of(mandrillResponse));
    when(emailService.newMandrillMessage(
            eq(user.getEmail()), eq("withdrawal_batch_en"), anyMap(), eq(tags), anyList()))
        .thenReturn(message);

    mandateBatchEmailService.sendMandateBatch(user, mandateBatch, pillarSuggestion, Locale.ENGLISH);

    verify(emailPersistenceService)
        .save(user, "123", EmailType.WITHDRAWAL_BATCH, "sent", mandateBatch);

    verify(emailService)
        .newMandrillMessage(
            eq(user.getEmail()),
            eq("withdrawal_batch_en"),
            anyMap(),
            eq(tags),
            argThat(
                attachments ->
                    attachments.size() == 1
                        && attachments.getFirst().getName().equals(file.getName())
                        && attachments.getFirst().getContent().equals(file.getContent())));
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
            "mandate_batch",
            "pillar_2",
            "pillar_3",
            "fund_pension_opening",
            "partial_withdrawal",
            "suggest_payment_rate",
            "suggest_2",
            "suggest_3");

    when(emailService.send(user, message, "withdrawal_batch_en"))
        .thenReturn(Optional.of(mandrillResponse));
    when(emailService.newMandrillMessage(
            eq(user.getEmail()), eq("withdrawal_batch_en"), anyMap(), eq(tags), anyList()))
        .thenReturn(message);

    mandateBatchEmailService.sendMandateBatch(user, mandateBatch, pillarSuggestion, Locale.ENGLISH);

    verify(emailPersistenceService)
        .save(user, "123", EmailType.WITHDRAWAL_BATCH, "sent", mandateBatch);

    verify(emailService)
        .newMandrillMessage(
            eq(user.getEmail()),
            eq("withdrawal_batch_en"),
            anyMap(),
            eq(tags),
            argThat(
                attachments ->
                    attachments.size() == 1
                        && attachments.getFirst().getName().equals(file.getName())
                        && attachments.getFirst().getContent().equals(file.getContent())));
  }
}
