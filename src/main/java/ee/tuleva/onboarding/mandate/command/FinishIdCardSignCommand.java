package ee.tuleva.onboarding.mandate.command;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FinishIdCardSignCommand {

  @NotBlank private String signedHash;
}
