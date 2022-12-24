package ee.tuleva.onboarding.epis.cancellation;

import ee.tuleva.onboarding.mandate.application.ApplicationType;
import ee.tuleva.onboarding.user.address.Address;
import java.time.Instant;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.springframework.lang.Nullable;

@Data
@Builder
public class CancellationDto {

  @NotNull private Long id;

  @NotNull private String processId;

  @NotNull private ApplicationType applicationTypeToCancel;

  @NotNull private Instant createdDate;

  @Valid @Nullable private Address address;

  private String email;

  private String phoneNumber;
}
