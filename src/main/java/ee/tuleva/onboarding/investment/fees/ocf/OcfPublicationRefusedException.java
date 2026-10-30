package ee.tuleva.onboarding.investment.fees.ocf;

public class OcfPublicationRefusedException extends RuntimeException {

  OcfPublicationRefusedException(String message) {
    super(message);
  }
}
