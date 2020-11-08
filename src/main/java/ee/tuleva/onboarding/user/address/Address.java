package ee.tuleva.onboarding.user.address;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.mandate.MandateView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonView(MandateView.Default.class)
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
