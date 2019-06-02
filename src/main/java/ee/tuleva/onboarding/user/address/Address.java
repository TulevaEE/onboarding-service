package ee.tuleva.onboarding.user.address;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Address {

    private String street;

    private String districtCode;

    private String postalCode;

    private String countryCode;

}
