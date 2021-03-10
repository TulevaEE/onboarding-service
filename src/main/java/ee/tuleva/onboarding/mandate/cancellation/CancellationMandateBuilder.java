package ee.tuleva.onboarding.mandate.cancellation;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.user.User;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CancellationMandateBuilder {

  private final ConversionDecorator conversionDecorator;

  public Mandate build(
      ApplicationDTO applicationToCancel,
      User user,
      ConversionResponse conversion,
      UserPreferences contactDetails) {

    Mandate mandate = new Mandate();
    mandate.setUser(user);
    mandate.setPillar(2);
    mandate.setAddress(contactDetails.getAddress());

    conversionDecorator.addConversionMetadata(mandate, conversion, contactDetails);

    if (applicationToCancel.getType() == WITHDRAWAL
        || applicationToCancel.getType() == EARLY_WITHDRAWAL) {
      return buildWithdrawalMandate(applicationToCancel, mandate);
    } else if (applicationToCancel.getType() == TRANSFER) {
      return buildTransferCancellationMandate(applicationToCancel, mandate);
    }
    return null;
  }

  public Mandate buildWithdrawalMandate(ApplicationDTO applicationToCancel, Mandate mandate) {
    mandate.putMetadata("applicationTypeToCancel", applicationToCancel.getType());
    return mandate;
  }

  private Mandate buildTransferCancellationMandate(
      ApplicationDTO applicationToCancel, Mandate mandate) {

    val exchange =
        FundTransferExchange.builder()
            .sourceFundIsin(applicationToCancel.getSourceFundIsin())
            .targetFundIsin(null)
            .mandate(mandate)
            .build();

    mandate.setFundTransferExchanges(Collections.singletonList(exchange));
    return mandate;
  }
}
