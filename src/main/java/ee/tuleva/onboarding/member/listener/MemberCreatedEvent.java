package ee.tuleva.onboarding.member.listener;

import ee.tuleva.onboarding.locale.LocaleConfiguration;
import ee.tuleva.onboarding.user.User;
import java.util.Locale;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class MemberCreatedEvent {
  private final User user;
  private Locale locale = LocaleConfiguration.DEFAULT_LOCALE;
}
