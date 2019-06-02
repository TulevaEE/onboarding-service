package ee.tuleva.onboarding.user.address;

import ee.tuleva.onboarding.auth.principal.Person;
import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private final EpisService episService;

    public UserPreferences updateAddress(Person person, Address address) {
        UserPreferences contactDetails = episService.getContactDetails(person);
        contactDetails.setAddress(address);
        return episService.updateContactDetails(contactDetails);
    }

}
