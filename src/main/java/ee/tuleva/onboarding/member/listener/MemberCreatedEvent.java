package ee.tuleva.onboarding.member.listener;

import ee.tuleva.onboarding.user.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public class MemberCreatedEvent {
    private final User user;
    private final Locale locale;
}