package ee.tuleva.onboarding.epis.cancellation;

import lombok.Builder;
import lombok.Data;
import org.springframework.lang.Nullable;
import ee.tuleva.onboarding.user.address.Address;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.time.Instant;

@Data
@Builder
public class CancellationDto {

    @NotNull
    private Long id;

    @NotNull
    private String processId;

    @NotNull
    private ApplicationTypeToCancel applicationTypeToCancel;

    @NotNull
    private Instant createdDate;

    @Valid
    @Nullable
    private Address address;

}
