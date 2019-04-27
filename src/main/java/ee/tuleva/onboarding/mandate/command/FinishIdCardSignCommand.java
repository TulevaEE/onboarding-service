package ee.tuleva.onboarding.mandate.command;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class FinishIdCardSignCommand {

    @NotBlank
    private String signedHash;

}
