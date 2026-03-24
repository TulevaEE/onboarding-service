package ee.tuleva.onboarding.ariregister;

import jakarta.annotation.Nullable;
import java.time.LocalDate;
import java.util.Optional;
import lombok.Value;

@Value
public class CompanyDetail {

  String name;
  String registryCode;
  @Nullable String status;
  @Nullable LocalDate foundingDate;
  @Nullable String address;
  @Nullable String mainActivity;

  public Optional<String> getStatus() {
    return Optional.ofNullable(status);
  }

  public Optional<LocalDate> getFoundingDate() {
    return Optional.ofNullable(foundingDate);
  }

  public Optional<String> getAddress() {
    return Optional.ofNullable(address);
  }

  public Optional<String> getMainActivity() {
    return Optional.ofNullable(mainActivity);
  }
}
