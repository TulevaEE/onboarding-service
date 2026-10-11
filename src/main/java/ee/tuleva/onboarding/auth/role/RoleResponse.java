package ee.tuleva.onboarding.auth.role;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(NON_NULL)
public record RoleResponse(RoleType type, String code, String name, @Nullable UUID id) {}
