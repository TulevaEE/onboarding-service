package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
import ee.tuleva.onboarding.mandate.event.OnMandateBatchFailedEvent;
import ee.tuleva.onboarding.paymentrate.PaymentRates;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
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

  @EventListener
  public void sendEmail(AfterMandateSignedEvent event) {
    ContactDetails contactDetails = episService.getContactDetails(event.user());
    ConversionResponse conversion = conversionService.getConversion(event.user());
    PaymentRates paymentRates = paymentRateService.getPaymentRates(event.user());
    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(event.user(), contactDetails, conversion, paymentRates);
    if (!event.mandate().isPartOfBatch()) {
      mandateEmailService.sendMandate(
          event.user(), event.mandate(), pillarSuggestion, event.locale());
    } else {
      log.info(
          "Skipping mandate email because it is part of a batch: mandateId={}",
          event.mandate().getId());
    }
  }

  @EventListener
  public void sendBatchEmail(AfterMandateBatchSignedEvent event) {
    ContactDetails contactDetails = episService.getContactDetails(event.user());
    ConversionResponse conversion = conversionService.getConversion(event.user());
    PaymentRates paymentRates = paymentRateService.getPaymentRates(event.user());
    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(event.user(), contactDetails, conversion, paymentRates);
    mandateBatchEmailService.sendMandateBatch(
        event.user(), event.mandateBatch(), pillarSuggestion, event.locale());
  }

  @EventListener
  public void sendBatchFailedEmail(OnMandateBatchFailedEvent event) {
    mandateBatchEmailService.sendMandateBatchFailedEmail(
        event.user(), event.mandateBatch(), event.locale());
  }
}
