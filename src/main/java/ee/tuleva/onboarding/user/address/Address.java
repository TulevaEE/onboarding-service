package ee.tuleva.onboarding.user.address;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.mandate.MandateView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Nullable;

import javax.validation.constraints.NotBlank;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonView(MandateView.Default.class)
@ValidAddress
public class Address {

    @NotBlank
    private String street;

    @Nullable // Only Estonian addresses must have a district code
    private String districtCode;

    @NotBlank
    private String postalCode;

    @NotBlank
    private String countryCode;

    @JsonIgnore
    public boolean isEstonian() {
        return "EE".equals(countryCode);
    }
}
