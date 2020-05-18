package ee.tuleva.onboarding.member.listener;

import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MemberCreatedEvent {
    private final User user;
}