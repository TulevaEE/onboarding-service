package ee.tuleva.onboarding.epis;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mock")
class MockRejoinPermissionSource implements RejoinPermissionSource {

  @Override
  public RejoinPermission getRejoinPermission() {
    return new RejoinPermission(true);
  }
}
