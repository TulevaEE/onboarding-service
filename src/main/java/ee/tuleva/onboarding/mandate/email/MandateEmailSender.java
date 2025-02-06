package ee.tuleva.onboarding.mandate.email;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.mandate.event.AfterMandateBatchSignedEvent;
import ee.tuleva.onboarding.mandate.event.AfterMandateSignedEvent;
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
    ContactDetails contactDetails = episService.getContactDetails(event.getUser());
    ConversionResponse conversion = conversionService.getConversion(event.getUser());
    PaymentRates paymentRates = paymentRateService.getPaymentRates(event.getUser());
    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(event.getUser(), contactDetails, conversion, paymentRates);
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
    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(event.getUser(), contactDetails, conversion, paymentRates);
    mandateBatchEmailService.sendMandateBatch(
        event.getUser(), event.getMandateBatch(), pillarSuggestion, event.getLocale());
  }

  @EventListener
  public void sendBatchFailedEmail(AfterMandateBatchSignedEvent event) {
    ContactDetails contactDetails = episService.getContactDetails(event.getUser());
    ConversionResponse conversion = conversionService.getConversion(event.getUser());
    PaymentRates paymentRates = paymentRateService.getPaymentRates(event.getUser());
    PillarSuggestion pillarSuggestion =
        new PillarSuggestion(event.getUser(), contactDetails, conversion, paymentRates);

    mandateBatchEmailService.sendMandateBatch(
        event.getUser(), event.getMandateBatch(), pillarSuggestion, event.getLocale());
  }
}
