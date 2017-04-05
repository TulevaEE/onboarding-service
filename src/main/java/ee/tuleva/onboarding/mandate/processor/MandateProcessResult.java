package ee.tuleva.onboarding.mandate.processor;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

@Getter
@Setter
@Builder
public class MandateProcessResult {

    private String processId;
    private boolean successful;
    private Integer errorCode;

    public Optional<Integer> getErrorCode() {
        return Optional.ofNullable(errorCode);
    }
}
