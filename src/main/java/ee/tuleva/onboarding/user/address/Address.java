package ee.tuleva.onboarding.user.address;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Address {

    @NotNull
    private String street;

    @NotNull
    private String districtCode;

    @NotNull
    private String postalCode;

    @NotNull
    private String countryCode;

}
