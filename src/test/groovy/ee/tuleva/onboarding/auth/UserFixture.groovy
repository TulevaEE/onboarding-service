package ee.tuleva.onboarding.auth


import java.time.Instant

import static ee.tuleva.onboarding.user.MemberFixture.sampleMember
import static ee.tuleva.onboarding.user.User.UserBuilder
import static ee.tuleva.onboarding.user.User.builder

class UserFixture {

    static UserBuilder sampleUser() {
        return builder()
                .id(999)
                .firstName("Jordan")
                .lastName("Valdma")
                .personalCode("38812121215")
                .email("jordan.valdma@gmail.com")
                .phoneNumber("5555555")
                .createdDate(Instant.parse("2017-01-31T10:06:01Z"))
                .updatedDate(Instant.parse("2017-01-31T10:06:01Z"))
                .active(true)
                .member(sampleMember)
    }

    static UserBuilder sampleUserNonMember() {
        return builder()
                .id(999)
                .firstName("Jordan")
                .lastName("Valdma")
                .personalCode("38812121215")
                .email("jordan.valdma@gmail.com")
                .phoneNumber("5555555")
                .createdDate(Instant.parse("2017-01-31T10:06:01Z"))
                .updatedDate(Instant.parse("2017-01-31T10:06:01Z"))
                .active(true)
                .member(null)
    }

}
