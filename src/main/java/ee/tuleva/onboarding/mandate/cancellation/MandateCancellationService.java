package ee.tuleva.onboarding.mandate.cancellation;

import static ee.tuleva.onboarding.mandate.application.ApplicationType.EARLY_WITHDRAWAL;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.TRANSFER;
import static ee.tuleva.onboarding.mandate.application.ApplicationType.WITHDRAWAL;
import static java.util.Arrays.asList;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.epis.mandate.ApplicationDTO;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateService;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MandateCancellationService {

  private final MandateService mandateService;
  private final UserService userService;
  private final EpisService episService;
  private final UserConversionService conversionService;
  private final CancellationMandateBuilder cancellationMandateBuilder;

  public Mandate saveCancellationMandate(Long userId, ApplicationDTO applicationToCancel) {
    validate(applicationToCancel.getType());
    User user = userService.getById(userId);
    ConversionResponse conversion = conversionService.getConversion(user);
    UserPreferences contactDetails = episService.getContactDetails(user);
    Mandate mandate =
        cancellationMandateBuilder.build(applicationToCancel, user, conversion, contactDetails);
    return mandateService.save(user, mandate);
  }

  private void validate(ApplicationType applicationTypeToCancel) {
    if (!asList(WITHDRAWAL, EARLY_WITHDRAWAL, TRANSFER).contains(applicationTypeToCancel)) {
      throw new InvalidApplicationTypeException(
          "Invalid application type: " + applicationTypeToCancel);
    }
  }
}
