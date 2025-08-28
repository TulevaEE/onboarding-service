package ee.tuleva.onboarding.mandate.cancellation;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL;
import static java.util.Collections.singletonList;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.epis.contact.ContactDetails;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.epis.mandate.details.EarlyWithdrawalCancellationMandateDetails;
import ee.tuleva.onboarding.epis.mandate.details.TransferCancellationMandateDetails;
import ee.tuleva.onboarding.epis.mandate.details.WithdrawalCancellationMandateDetails;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
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
  private final SecondPillarPaymentRateService secondPillarPaymentRateService;

  public Mandate build(
      ApplicationDTO applicationToCancel,
      AuthenticatedPerson authenticatedPerson,
      User user,
      ConversionResponse conversion,
      ContactDetails contactDetails) {

    Mandate mandate = new Mandate();
    mandate.setUser(user);
    mandate.setAddress(contactDetails.getAddress());

    var paymentRates = secondPillarPaymentRateService.getPaymentRates(authenticatedPerson);
    conversionDecorator.addConversionMetadata(
        mandate.getMetadata(), conversion, contactDetails, authenticatedPerson, paymentRates);

    if (applicationToCancel.getType() == WITHDRAWAL) {
      return buildWithdrawalCancellationMandate(mandate);
    } else if (applicationToCancel.getType() == EARLY_WITHDRAWAL) {
      return buildEarlyWithdrawalCancellationMandate(mandate);
    } else if (applicationToCancel.getType() == TRANSFER) {
      return buildTransferCancellationMandate(applicationToCancel, mandate);
    }
    return null;
  }

  public Mandate buildWithdrawalCancellationMandate(Mandate mandate) {
    // TODO legacy fields
    mandate.setPillar(2);

    mandate.setDetails(new WithdrawalCancellationMandateDetails());
    return mandate;
  }

  public Mandate buildEarlyWithdrawalCancellationMandate(Mandate mandate) {
    // TODO legacy fields
    mandate.setPillar(2);

    mandate.setDetails(new EarlyWithdrawalCancellationMandateDetails());
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

    var exchanges = singletonList(exchange);

    // TODO legacy fields
    mandate.setPillar(sourceFund.getPillar());
    mandate.setFundTransferExchanges(exchanges);

    mandate.setDetails(
        TransferCancellationMandateDetails.fromFundTransferExchanges(
            exchanges, sourceFund.getPillar()));

    return mandate;
  }
}
