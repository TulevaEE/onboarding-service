package ee.tuleva.onboarding.populationregister;

public record Guardian(String personalCode, boolean assetManagement, boolean valid, boolean alive) {

  public boolean grantsAssetManagement() {
    return assetManagement && valid && alive;
  }
}
