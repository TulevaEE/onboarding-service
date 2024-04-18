package ee.tuleva.onboarding.mandate.cancellation;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL;
import static java.util.Collections.singletonList;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CancellationMandateBuilder {

  private final ConversionDecorator conversionDecorator;
  private final FundRepository fundRepository;

  public Mandate build(
      ApplicationDTO applicationToCancel,
      AuthenticatedPerson authenticatedPerson,
      User user,
      ConversionResponse conversion,
      ContactDetails contactDetails) {

    Mandate mandate = new Mandate();
    mandate.setUser(user);
    mandate.setAddress(contactDetails.getAddress());

    conversionDecorator.addConversionMetadata(
        mandate.getMetadata(), conversion, contactDetails, authenticatedPerson);

    if (applicationToCancel.getType() == WITHDRAWAL
        || applicationToCancel.getType() == EARLY_WITHDRAWAL) {
      return buildWithdrawalMandate(applicationToCancel, mandate);
    } else if (applicationToCancel.getType() == TRANSFER) {
      return buildTransferCancellationMandate(applicationToCancel, mandate);
    }
    return null;
  }

  public Mandate buildWithdrawalMandate(ApplicationDTO applicationToCancel, Mandate mandate) {
    mandate.setPillar(2);
    mandate.putMetadata("applicationTypeToCancel", applicationToCancel.getType());
    return mandate;
  }

  private Mandate buildTransferCancellationMandate(
      ApplicationDTO applicationToCancel, Mandate mandate) {
    Fund sourceFund = fundRepository.findByIsin(applicationToCancel.getSourceFundIsin());

    final var exchange =
        FundTransferExchange.builder()
            .sourceFundIsin(sourceFund.getIsin())
            .targetFundIsin(null)
            .amount(null)
            .mandate(mandate)
            .build();

    mandate.setPillar(sourceFund.getPillar());
    mandate.setFundTransferExchanges(singletonList(exchange));
    return mandate;
  }
}
