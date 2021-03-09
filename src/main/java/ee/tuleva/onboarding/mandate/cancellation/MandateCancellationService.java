package ee.tuleva.onboarding.mandate.cancellation;

import ee.tuleva.onboarding.conversion.ConversionResponse;
import ee.tuleva.onboarding.conversion.UserConversionService;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.mandate.application.ApplicationType;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import ee.tuleva.onboarding.mandate.Mandate;
import ee.tuleva.onboarding.mandate.MandateService;
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

    public Mandate saveCancellationMandate(Long userId, ApplicationType applicationTypeToCancel) {
        User user = userService.getById(userId);
        ConversionResponse conversion = conversionService.getConversion(user);
        UserPreferences contactDetails = episService.getContactDetails(user);
        Mandate mandate = cancellationMandateBuilder.build(applicationTypeToCancel, user, conversion, contactDetails);
        return mandateService.save(user, mandate);
    }

}
