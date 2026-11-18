package ee.tuleva.onboarding.user;

import org.jspecify.annotations.Nullable;

public final class UserContactDetailsTestFixture {

  private UserContactDetailsTestFixture() {}

  public static User withContactDetails(
      User user, @Nullable String email, @Nullable String phoneNumber) {
    user.setEmail(email);
    user.setPhoneNumber(phoneNumber);
    return user;
  }
}
