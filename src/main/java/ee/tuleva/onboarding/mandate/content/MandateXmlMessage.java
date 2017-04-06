package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.mandate.MandateApplicationType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MandateXmlMessage {
    private String processId;
    private String message;
    private MandateApplicationType type;

}
