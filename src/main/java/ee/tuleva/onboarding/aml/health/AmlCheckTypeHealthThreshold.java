package ee.tuleva.onboarding.aml.health;

public interface AmlCheckTypeHealthThreshold {
  String getType();

  Double getMaxIntervalSeconds();
}
