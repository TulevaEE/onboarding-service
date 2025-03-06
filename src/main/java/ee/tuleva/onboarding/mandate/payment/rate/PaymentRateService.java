package ee.tuleva.onboarding.mandate.payment.rate;

import static ee.tuleva.onboarding.epis.mandate.details.PaymentRateChangeMandateDetails.PaymentRate.fromValue;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.mandate.details.PaymentRateChangeMandateDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.generic.GenericMandateService;
import ee.tuleva.onboarding.mandate.generic.MandateDto;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentRateService {

  private final GenericMandateService genericMandateService;

  public Mandate savePaymentRateMandate(
      AuthenticatedPerson authenticatedPerson, BigDecimal paymentRate) {
    var mandateDetails = new PaymentRateChangeMandateDetails(fromValue(paymentRate));
    var mandateDto = MandateDto.builder().details(mandateDetails).build();

    return genericMandateService.createGenericMandate(authenticatedPerson, mandateDto);
  }
}
