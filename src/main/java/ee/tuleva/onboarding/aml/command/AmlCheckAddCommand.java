package ee.tuleva.onboarding.aml.command;

import ee.tuleva.onboarding.aml.AmlCheckType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AmlCheckAddCommand {
    @ValidAmlCheckType
    private AmlCheckType type;
    private boolean success;
}
