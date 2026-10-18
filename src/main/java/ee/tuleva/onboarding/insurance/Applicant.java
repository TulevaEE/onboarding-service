package ee.tuleva.onboarding.insurance;

import org.jspecify.annotations.Nullable;

record Applicant(
    String firstName,
    String lastName,
    String personalCode,
    @Nullable String email,
    @Nullable String phoneNumber,
    String pensionAccountNumber) {

  String fullName() {
    return firstName + " " + lastName;
  }
}
