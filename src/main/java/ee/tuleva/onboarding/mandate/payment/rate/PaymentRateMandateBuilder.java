package ee.tuleva.onboarding.mandate.payment.rate;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.user.User;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentRateMandateBuilder {

  private final ConversionDecorator conversionDecorator;

  public Mandate build(
      BigDecimal paymentRate,
      User user,
      ConversionResponse conversion,
      ContactDetails contactDetails) {

    Mandate mandate = new Mandate();
    mandate.setUser(user);
    mandate.setAddress(contactDetails.getAddress());
    mandate.setPillar(2);

    conversionDecorator.addConversionMetadata(mandate.getMetadata(), conversion, contactDetails);
    mandate.setPaymentRate(paymentRate);

    return mandate;
  }
}
