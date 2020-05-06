package ee.tuleva.onboarding.member.event;

import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MemberApplicationEvent {
    private final String name;
    private final User user;
}