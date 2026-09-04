package ee.tuleva.onboarding.signature;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record StartIdCardSignCommand(
    @NotBlank String certificate, @NotEmpty List<String> supportedHashFunctions) {}
