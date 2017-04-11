package ee.tuleva.onboarding.mandate.content;

import ee.tuleva.onboarding.mandate.MandateApplicationType;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MandateXmlMessage {
    private String processId;
    private String message;
    private MandateApplicationType type;

}
