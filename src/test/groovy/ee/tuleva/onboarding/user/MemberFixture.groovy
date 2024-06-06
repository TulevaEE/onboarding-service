package ee.tuleva.onboarding.user

import ee.tuleva.onboarding.user.member.Member

import java.time.Instant

import static ee.tuleva.onboarding.auth.UserFixture.sampleUser

class MemberFixture {

    public static Member sampleMember = Member.builder()
            .user(sampleUser().build())
            .createdDate(Instant.parse("2017-01-31T10:06:01Z"))
            .memberNumber(1234567)
    .build()

    static Member.MemberBuilder memberFixture() {
        return Member.builder()
            .user(sampleUser().build())
            .createdDate(Instant.parse("2017-01-31T10:06:01Z"))
            .memberNumber(1234567)
    }

}
