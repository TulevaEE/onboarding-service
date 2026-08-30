package ee.tuleva.onboarding.mandate.email;

import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.analytics.RecurringSavers;
import ee.tuleva.onboarding.analytics.SecondPillarLeavers;
import ee.tuleva.onboarding.contribution.ThirdPillarTaxHeadroom;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateContactDetails;
import ee.tuleva.onboarding.mandate.MandateContacts;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.PillarSuggestion;
import ee.tuleva.onboarding.mandate.SavingsFundSaverStatus;
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.event.OnMandateBatchFailedEvent;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MandateEmailSender {

  private final MandateEmailService mandateEmailService;
  private final MandateBatchEmailService mandateBatchEmailService;
  private final MandateContacts mandateContacts;
  private final UserConversionService conversionService;
  private final SecondPillarPaymentRateService paymentRateService;
  private final SecondPillarLeavers secondPillarLeavers;
  private final SavingsFundSaverStatus savingsFundSaverStatus;
  private final RecurringSavers recurringSavers;
  private final ThirdPillarTaxHeadroom thirdPillarTaxHeadroom;

  @EventListener
  public void sendEmail(AfterMandateSignedEvent event) {
    MandateContactDetails contactDetails = mandateContacts.getContactDetails(event.getUser());
    ConversionResponse conversion = conversionService.getConversion(event.getUser());
    PaymentRates paymentRates = paymentRateService.getPaymentRates(event.getUser());
    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(
            event.getUser(),
            contactDetails.secondPillarActive(),
            contactDetails.thirdPillarActive(),
            conversion,
            paymentRates,
            Set.of(event.getMandate().getPillar()),
            secondPillarLeavers.hasLeft(event.getUser().getPersonalCode()),
            savingsFundSaverStatus.isSaver(event.getUser().getPersonalCode()),
            event.getMandate().getMandateType() == MandateType.PAYMENT_RATE_CHANGE,
            recurringSavers.recurringPaymentsOf(event.getUser().getPersonalCode()),
            thirdPillarTaxHeadroom.hasHeadroom(event.getUser()));
    if (!event.getMandate().isPartOfBatch()) {
      mandateEmailService.sendMandate(
          event.getUser(), event.getMandate(), pillarSuggestion, event.getLocale());
    } else {
      log.info(
          "Skipping mandate email because it is part of a batch: mandateId={}",
          event.getMandate().getId());
    }
  }

  @EventListener
  public void sendBatchEmail(AfterMandateBatchSignedEvent event) {
    MandateContactDetails contactDetails = mandateContacts.getContactDetails(event.getUser());
    ConversionResponse conversion = conversionService.getConversion(event.getUser());
    PaymentRates paymentRates = paymentRateService.getPaymentRates(event.getUser());
    Set<Integer> mandatePillars =
        event.getMandateBatch().getMandates().stream().map(Mandate::getPillar).collect(toSet());
    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(
            event.getUser(),
            contactDetails.secondPillarActive(),
            contactDetails.thirdPillarActive(),
            conversion,
            paymentRates,
            mandatePillars,
            secondPillarLeavers.hasLeft(event.getUser().getPersonalCode()),
            savingsFundSaverStatus.isSaver(event.getUser().getPersonalCode()),
            false,
            recurringSavers.recurringPaymentsOf(event.getUser().getPersonalCode()),
            thirdPillarTaxHeadroom.hasHeadroom(event.getUser()));
    mandateBatchEmailService.sendMandateBatch(
        event.getUser(), event.getMandateBatch(), pillarSuggestion, event.getLocale());
  }

  @EventListener
  public void sendBatchFailedEmail(OnMandateBatchFailedEvent event) {
    mandateBatchEmailService.sendMandateBatchFailedEmail(
        event.getUser(), event.getMandateBatch(), event.getLocale());
  }
}
