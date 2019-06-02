package ee.tuleva.onboarding.user.address;

import ee.tuleva.onboarding.auth.principal.AuthenticatedPerson;
import ee.tuleva.onboarding.epis.contact.UserPreferences;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import javax.validation.Valid;

@RestController
@RequestMapping("/v1")
@AllArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @ApiOperation(value = "Update the current user's address")
    @PatchMapping("/me/address")
    public Address patchAddress(@ApiIgnore @AuthenticationPrincipal AuthenticatedPerson authenticatedPerson,
                                @Valid @RequestBody Address address) {

        UserPreferences contactDetails = addressService.updateAddress(authenticatedPerson, address);

        return contactDetails.getAddress();
    }

}
