package ee.tuleva.onboarding.auth.smartid;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;

public record StartSmartIdLoginCommand(
    @NotNull SmartIdLoginFlow flow,
    @Nullable @Pattern(regexp = "[a-z]{2}") String language,
    @JsonSetter(nulls = Nulls.AS_EMPTY) boolean rememberMe) {}
