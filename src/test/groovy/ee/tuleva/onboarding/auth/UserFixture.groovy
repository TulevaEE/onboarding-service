package ee.tuleva.onboarding.auth;

import ee.tuleva.onboarding.user.User;

import java.time.Instant;

public class UserFixture {

    public static User sampleUser() {
        return User.builder()
                .firstName("Jordan")
                .lastName("Valdma")
                .personalCode("38812121212")
                .email("jordan.valdma@gmail.com")
                .phoneNumber("5555555")
                .createdDate(Instant.parse("2017-01-31T10:06:01Z"))
                .updatedDate(Instant.parse("2017-01-31T10:06:01Z"))
                .memberNumber(0)
                .active(true)
                .build()
    }
}
