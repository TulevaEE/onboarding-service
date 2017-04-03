package ee.tuleva.onboarding.mandate.processor;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class MandateProcessResult {

    public enum ProcessResult {
        SUCCESS, FAIL
    }

    private String processId;
    private ProcessResult result;
}
