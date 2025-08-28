package ee.tuleva.onboarding.mandate.generic;

import static ee.tuleva.onboarding.pillar.Pillar.SECOND;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.mandate.details.SelectionMandateDetails;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateType;
import ee.tuleva.onboarding.mandate.builder.ConversionDecorator;
import ee.tuleva.onboarding.paymentrate.SecondPillarPaymentRateService;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import org.springframework.stereotype.Component;

// TODO currently unused – needs to be migrated together with transfer mandate via mandate batch
@Component
public class SelectionMandateFactory extends MandateFactory<SelectionMandateDetails> {

  public SelectionMandateFactory(
      UserService userService,
      EpisService episService,
      UserConversionService conversionService,
      ConversionDecorator conversionDecorator,
      SecondPillarPaymentRateService secondPillarPaymentRateService) {
    super(
        userService,
        episService,
        conversionService,
        conversionDecorator,
        secondPillarPaymentRateService);
  }

  @Override
  Mandate createMandate(
      AuthenticatedPerson authenticatedPerson,
      MandateDto<SelectionMandateDetails> mandateCreationDto) {
    Mandate mandate = this.setupMandate(authenticatedPerson, mandateCreationDto);
    SelectionMandateDetails details = mandateCreationDto.getDetails();

    mandate.setPillar(SECOND.toInt());
    // TODO legacy fields
    mandate.setFutureContributionFundIsin(details.getFutureContributionFundIsin());
    mandate.setFundTransferExchanges(List.of());

    return mandate;
  }

  @Override
  public boolean supports(MandateType mandateType) {
    return mandateType == MandateType.SELECTION;
  }
}
