package ee.tuleva.onboarding.mandate.generic;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.mandate.GenericMandateCreationDto;
import ee.tuleva.onboarding.mandate.*;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GenericMandateService {
  private final List<MandateFactory> mandateFactories;
  private final MandateService mandateService;
  private final UserService userService;

  public Mandate createGenericMandate(AuthenticatedPerson authenticatedPerson, GenericMandateCreationDto<?> mandateCreationDto) {
    MandateType mandateType = mandateCreationDto.getDetails().getMandateType();

    Mandate mandate =
        mandateFactories.stream()
            .filter(mandateFactory -> mandateFactory.supports(mandateType))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Unsupported mandateType: " + mandateType))
            .createMandate(authenticatedPerson, mandateCreationDto);

    User user = userService.getById(authenticatedPerson.getUserId());
    mandateService.save(user, mandate);

    return mandate;
  }

}
