package ee.tuleva.onboarding.mandate.command;

import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

@Data
public class StartIdCardSignCommand {

    @NotBlank
    private String clientCertificate;

}
