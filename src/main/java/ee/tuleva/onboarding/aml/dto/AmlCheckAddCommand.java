package ee.tuleva.onboarding.aml.dto;

import ee.tuleva.onboarding.aml.AmlCheckType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AmlCheckAddCommand {
    @ValidAmlCheckType
    private AmlCheckType type;
    private boolean success;
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
