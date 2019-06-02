package ee.tuleva.onboarding.epis.contact;

import ee.tuleva.onboarding.epis.EpisService;
import ee.tuleva.onboarding.user.User;
import ee.tuleva.onboarding.user.address.Address;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactDetailsService {

    private final EpisService episService;

    public UserPreferences updateContactDetails(User user, Address address) {
        UserPreferences contactDetails = episService.getContactDetails(user);
        contactDetails.setEmail(user.getEmail());
        contactDetails.setPhoneNumber(user.getPhoneNumber());
        contactDetails.setAddress(address);
        return episService.updateContactDetails(user, contactDetails);
    }

}
