package ee.tuleva.onboarding.user.address;

import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.mandate.MandateView;
import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonView(MandateView.Default.class)
@ValidAddress
public class Address {

  @NotBlank private String countryCode;
}
