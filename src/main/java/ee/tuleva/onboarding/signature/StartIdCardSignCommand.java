package ee.tuleva.onboarding.signature;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StartIdCardSignCommand(
    @NotBlank String certificate,
    @NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 20) String> supportedHashFunctions) {}
