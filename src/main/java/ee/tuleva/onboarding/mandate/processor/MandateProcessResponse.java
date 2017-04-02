package ee.tuleva.onboarding.mandate.processor;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MandateProcessResponse {

    public enum ProcessResponse {
        SUCCESS, FAIL
    }

    private String processId;
    private ProcessResponse response;
}
