package ee.tuleva.onboarding.auth.principal;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class PersonImpl implements Person {
  String personalCode;
  String firstName;
  String lastName;
}
