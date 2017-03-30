package ee.tuleva.onboarding.mandate.content;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MandateXmlMessage {

    private String id;
    private String message;

}
