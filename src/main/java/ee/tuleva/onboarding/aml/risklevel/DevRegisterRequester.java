package ee.tuleva.onboarding.aml.risklevel;

import ee.tuleva.onboarding.aml.RegisterRequester;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"dev", "staging"})
class DevRegisterRequester implements RegisterRequester {

  @Override
  public Optional<String> designatedXRoadUserId() {
    return Optional.empty();
  }
}
