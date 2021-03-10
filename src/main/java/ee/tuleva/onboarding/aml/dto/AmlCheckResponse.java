package ee.tuleva.onboarding.aml.dto;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import ee.tuleva.onboarding.aml.AmlCheckType;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

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
