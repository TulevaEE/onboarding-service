package ee.tuleva.onboarding.mandate.generic;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.mandate.details.MandateDetails;
import ee.tuleva.onboarding.mandate.*;
import ee.tuleva.onboarding.mandate.batch.MandateBatch;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenericMandateService {
  private final List<MandateFactory<? extends MandateDetails>> mandateFactories;
  private final MandateService mandateService;
  private final UserService userService;

  @SuppressWarnings("unchecked")
  public <T extends MandateDetails> Mandate createGenericMandate(
      AuthenticatedPerson authenticatedPerson, MandateDto<?> mandateCreationDto) {
    MandateType mandateType = mandateCreationDto.getDetails().getMandateType();

    Mandate mandate =
        mandateFactories.stream()
            .filter(mandateFactory -> mandateFactory.supports(mandateType))
            .map(mandateFactory -> (MandateFactory<T>) mandateFactory)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unsupported mandateType: " + mandateType))
            .createMandate(authenticatedPerson, (MandateDto<T>) mandateCreationDto);

    User user = userService.getById(authenticatedPerson.getUserId()).orElseThrow();
    mandateService.save(user, mandate);

    return mandate;
  }

  @SuppressWarnings("unchecked")
  public <T extends MandateDetails> Mandate createGenericMandate(
      AuthenticatedPerson authenticatedPerson,
      MandateDto<?> mandateCreationDto,
      MandateBatch batch) {
    MandateType mandateType = mandateCreationDto.getDetails().getMandateType();

    Mandate mandate =
        mandateFactories.stream()
            .filter(mandateFactory -> mandateFactory.supports(mandateType))
            .map(mandateFactory -> (MandateFactory<T>) mandateFactory)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unsupported mandateType: " + mandateType))
            .createMandate(authenticatedPerson, (MandateDto<T>) mandateCreationDto);

    User user = userService.getById(authenticatedPerson.getUserId()).orElseThrow();

    mandate.setMandateBatch(batch);
    mandateService.save(user, mandate);

    return mandate;
  }
}
