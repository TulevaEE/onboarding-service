package ee.tuleva.onboarding.investment.kid;

class KidPublicationRefusedException extends RuntimeException {

  KidPublicationRefusedException(String message) {
    super(message);
  }

  KidPublicationRefusedException(String message, Throwable cause) {
    super(message, cause);
  }
}
