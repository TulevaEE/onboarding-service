package ee.tuleva.onboarding.auth.webeid;

import static org.assertj.core.api.Assertions.assertThat;

import eu.webeid.security.challenge.ChallengeNonce;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class SessionBackedChallengeNonceStoreTest {

  @Test
  void storesAndRetrievesChallengeNonce() {
    var session = new MockHttpSession();
    var store = new SessionBackedChallengeNonceStore(() -> session);
    var expirationTime = ZonedDateTime.now().plusMinutes(5);
    var nonce = new ChallengeNonce("test-nonce", expirationTime);

    store.put(nonce);
    var retrieved = store.getAndRemoveImpl();

    assertThat(retrieved.getBase64EncodedNonce()).isEqualTo("test-nonce");
    assertThat(retrieved.getExpirationTime()).isEqualTo(expirationTime);
  }

  @Test
  void removesNonceAfterRetrieval() {
    var session = new MockHttpSession();
    var store = new SessionBackedChallengeNonceStore(() -> session);
    var nonce = new ChallengeNonce("test-nonce", ZonedDateTime.now().plusMinutes(5));

    store.put(nonce);
    store.getAndRemoveImpl();
    var secondRetrieval = store.getAndRemoveImpl();

    assertThat(secondRetrieval).isNull();
  }
}
