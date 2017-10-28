package ee.tuleva.onboarding.epis;

public class EpisServiceException extends RuntimeException {

  public EpisServiceException(String message, Throwable cause) {
    super(message, cause);
  }
}