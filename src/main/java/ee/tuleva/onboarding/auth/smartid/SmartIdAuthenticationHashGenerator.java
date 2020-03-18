package ee.tuleva.onboarding.auth.smartid;

import ee.sk.smartid.AuthenticationHash;
import org.springframework.stereotype.Service;

@Service
public class SmartIdAuthenticationHashGenerator {

  public AuthenticationHash generateHash() {
    return AuthenticationHash.generateRandomHash();
  }
}
