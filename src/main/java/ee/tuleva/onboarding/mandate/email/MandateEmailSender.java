package ee.tuleva.onboarding.mandate.email;

import static java.util.stream.Collectors.toSet;

import ee.tuleva.onboarding.analytics.RecurringSavers;
import ee.tuleva.onboarding.analytics.SecondPillarLeavers;
import ee.tuleva.onboarding.contribution.ThirdPillarTaxHeadroom;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.ContactDetails;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.PillarSuggestion;
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.event.OnMandateBatchFailedEvent;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.savings.SavingsFundSavers;
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
  private final EpisService episService;
  private final UserConversionService conversionService;
  private final SecondPillarPaymentRateService paymentRateService;
  private final SecondPillarLeavers secondPillarLeavers;
  private final SavingsFundSavers savingsFundSavers;
  private final RecurringSavers recurringSavers;
  private final ThirdPillarTaxHeadroom thirdPillarTaxHeadroom;

  @EventListener
  public void sendEmail(AfterMandateSignedEvent event) {
    ContactDetails contactDetails = episService.getContactDetails(event.getUser());
    ConversionResponse conversion = conversionService.getConversion(event.getUser());
    PaymentRates paymentRates = paymentRateService.getPaymentRates(event.getUser());
    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(
            event.getUser(),
            contactDetails,
            conversion,
            paymentRates,
            Set.of(event.getMandate().getPillar()),
            secondPillarLeavers.hasLeft(event.getUser().getPersonalCode()),
            savingsFundSavers.isSaver(event.getUser().getPersonalCode()),
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
    ContactDetails contactDetails = episService.getContactDetails(event.getUser());
    ConversionResponse conversion = conversionService.getConversion(event.getUser());
    PaymentRates paymentRates = paymentRateService.getPaymentRates(event.getUser());
    Set<Integer> mandatePillars =
        event.getMandateBatch().getMandates().stream().map(Mandate::getPillar).collect(toSet());
    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(
            event.getUser(),
            contactDetails,
            conversion,
            paymentRates,
            mandatePillars,
            secondPillarLeavers.hasLeft(event.getUser().getPersonalCode()),
            savingsFundSavers.isSaver(event.getUser().getPersonalCode()),
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
