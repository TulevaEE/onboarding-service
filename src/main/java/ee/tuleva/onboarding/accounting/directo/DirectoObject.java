package ee.tuleva.onboarding.accounting.directo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DirectoObject(@Nullable String code, @Nullable String level) {}
