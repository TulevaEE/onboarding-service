package ee.tuleva.onboarding.mandate.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FinishIdCardSignCommand {

  @NotBlank private String signedHash;
}
