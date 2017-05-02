package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.auth.UserFixture
import ee.tuleva.onboarding.user.member.Member

import java.time.Instant

class MemberFixture {

    public static Member sampleMember = Member.builder()
            .user(UserFixture.sampleUser)
            .createdDate(Instant.parse("2017-01-31T10:06:01Z"))
            .memberNumber(1234567)
    .build();

}
