package ee.tuleva.onboarding.populationregister;

public class PopulationRegisterException extends RuntimeException {

  public PopulationRegisterException(String message) {
    super(message);
  }

  public PopulationRegisterException(String message, Throwable cause) {
    super(message, cause);
  }
}
