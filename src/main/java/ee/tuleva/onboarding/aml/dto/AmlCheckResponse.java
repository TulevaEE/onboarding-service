package ee.tuleva.onboarding.aml.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ee.tuleva.onboarding.aml.AmlCheckType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Value
@Builder
@AllArgsConstructor
public class AmlCheckResponse {
    AmlCheckType type;
    boolean success;
    @JsonInclude(NON_NULL)
    Map<String, Object> metadata;

    public AmlCheckResponse(AmlCheckAddCommand command) {
        this.type = command.getType();
        this.success = command.isSuccess();
        this.metadata = command.getMetadata();
    }
}
