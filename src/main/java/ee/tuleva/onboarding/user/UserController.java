package ee.tuleva.onboarding.user;

import ee.eesti.xtee6.kpr.PersonDataResponseType;
import ee.tuleva.onboarding.kpr.KPRClient;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/v1")
@AllArgsConstructor
public class UserController {

	@Autowired
	private final KPRClient kprClient;

	@ApiOperation(value = "Get info about the current user")
	@RequestMapping(method = GET, value = "/me")
	public User user(@ApiIgnore @AuthenticationPrincipal User user) {
		return user;
	}


	@ApiOperation(value = "Get info about the current user preferences from CSD")
	@RequestMapping(method = GET, value = "/preferences")
	public UserPreferences getPreferences(@ApiIgnore @AuthenticationPrincipal User user) {
		PersonDataResponseType csdPersonData = kprClient.personData(user.getPersonalCode());

		return UserPreferences.builder()
				.addressRow1(csdPersonData.getContactData().getAddressRow1())
				.addressRow2(csdPersonData.getContactData().getAddressRow2())
				.addressRow3(csdPersonData.getContactData().getAddressRow3())
				.country(csdPersonData.getContactData().getCountry().value())
				.postalIndex(csdPersonData.getContactData().getPostalIndex())
				.contactPreference(UserPreferences.ContactPreferenceType.valueOf(csdPersonData.getContactData().getContactPreference().value()))
				.build();
	}


}