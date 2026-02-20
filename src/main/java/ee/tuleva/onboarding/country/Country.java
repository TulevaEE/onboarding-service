package ee.tuleva.onboarding.country;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import ee.tuleva.onboarding.mandate.MandateView;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonView(MandateView.Default.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Country implements Serializable {

  @NotBlank private String countryCode;
}
