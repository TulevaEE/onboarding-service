package ee.tuleva.onboarding.ariregister;

import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class CompanyDetail {

  String name;
  String registryCode;
  @Nullable String status;
  @Nullable String legalForm;
  @Nullable LocalDate foundingDate;
  @Nullable CompanyAddress address;
  @Nullable String mainActivity;
  @Nullable String naceCode;
  @Singular List<String> activityCodes;
  @Singular List<Shareholding> shareholdings;
  @Singular List<RepresentationRight> representationRights;

  public Optional<String> getStatus() {
    return Optional.ofNullable(status);
  }

  public Optional<String> getLegalForm() {
    return Optional.ofNullable(legalForm);
  }

  public Optional<LocalDate> getFoundingDate() {
    return Optional.ofNullable(foundingDate);
  }

  public Optional<CompanyAddress> getAddress() {
    return Optional.ofNullable(address);
  }

  public Optional<String> getMainActivity() {
    return Optional.ofNullable(mainActivity);
  }

  public Optional<String> getNaceCode() {
    return Optional.ofNullable(naceCode);
  }
}
