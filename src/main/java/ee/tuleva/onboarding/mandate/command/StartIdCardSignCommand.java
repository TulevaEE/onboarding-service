package ee.tuleva.onboarding.mandate.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StartIdCardSignCommand {

  @NotBlank private String clientCertificate;
}
