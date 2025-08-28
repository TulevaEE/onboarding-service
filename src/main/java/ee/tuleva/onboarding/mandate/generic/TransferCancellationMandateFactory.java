package ee.tuleva.onboarding.mandate.generic;

import static ee.tuleva.onboarding.mandate.MandateType.*;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.details.TransferCancellationMandateDetails;
import ee.tuleva.onboarding.fund.Fund;
import ee.tuleva.onboarding.fund.FundRepository;
import ee.tuleva.onboarding.mandate.FundTransferExchange;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TransferCancellationMandateFactory
    extends MandateFactory<TransferCancellationMandateDetails> {
  private final FundRepository fundRepository;

  public TransferCancellationMandateFactory(
      UserService userService,
      EpisService episService,
      UserConversionService conversionService,
      ConversionDecorator conversionDecorator,
      SecondPillarPaymentRateService secondPillarPaymentRateService,
      FundRepository fundRepository) {
    super(
        userService,
        episService,
        conversionService,
        conversionDecorator,
        secondPillarPaymentRateService);
    this.fundRepository = fundRepository;
  }

  @Override
  public Mandate createMandate(
      AuthenticatedPerson authenticatedPerson,
      MandateDto<TransferCancellationMandateDetails> mandateCreationDto) {
    Mandate mandate = this.setupMandate(authenticatedPerson, mandateCreationDto);

    Fund sourceFund =
        fundRepository.findByIsin(
            mandateCreationDto.getDetails().getSourceFundIsinOfTransferToCancel());

    final var exchange =
        FundTransferExchange.builder()
            .sourceFundIsin(sourceFund.getIsin())
            .targetFundIsin(null)
            .amount(null)
            .mandate(mandate)
            .build();

    // TODO legacy fields
    mandate.setPillar(sourceFund.getPillar());
    mandate.setFundTransferExchanges(List.of(exchange));

    return mandate;
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == TRANSFER_CANCELLATION;
  }
}
