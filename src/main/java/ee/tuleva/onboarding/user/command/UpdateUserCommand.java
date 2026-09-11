package ee.tuleva.onboarding.user.command;

import ee.tuleva.onboarding.country.Country;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

public record UpdateUserCommand(
    @NotNull @Email String email, @Nullable String phoneNumber, @Valid @Nullable Country address) {}
