package ee.tuleva.onboarding.accounting.directo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DirectoDatafield(
    @Nullable String code, @Nullable String param, @Nullable String content) {}
