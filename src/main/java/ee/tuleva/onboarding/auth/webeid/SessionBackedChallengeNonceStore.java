package ee.tuleva.onboarding.auth.webeid;

import eu.webeid.security.challenge.ChallengeNonce;
import eu.webeid.security.challenge.ChallengeNonceStore;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.ObjectFactory;

public class SessionBackedChallengeNonceStore implements ChallengeNonceStore {

  private static final String CHALLENGE_NONCE_KEY = "challenge-nonce";

  private final ObjectFactory<HttpSession> httpSessionFactory;

  public SessionBackedChallengeNonceStore(ObjectFactory<HttpSession> httpSessionFactory) {
    this.httpSessionFactory = httpSessionFactory;
  }

  @Override
  public void put(ChallengeNonce challengeNonce) {
    var wrapper =
        new SerializableChallengeNonce(
            challengeNonce.getBase64EncodedNonce(), challengeNonce.getExpirationTime());
    currentSession().setAttribute(CHALLENGE_NONCE_KEY, wrapper);
  }

  @Override
  public ChallengeNonce getAndRemoveImpl() {
    var wrapper = (SerializableChallengeNonce) currentSession().getAttribute(CHALLENGE_NONCE_KEY);
    currentSession().removeAttribute(CHALLENGE_NONCE_KEY);
    if (wrapper == null) {
      return null;
    }
    return new ChallengeNonce(wrapper.base64EncodedNonce(), wrapper.expirationTime());
  }

  private HttpSession currentSession() {
    return httpSessionFactory.getObject();
  }

  private record SerializableChallengeNonce(String base64EncodedNonce, ZonedDateTime expirationTime)
      implements Serializable {}
}
