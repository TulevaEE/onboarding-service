package ee.tuleva.onboarding.mandate.application;

interface SecondPillarApplicationDetails extends ApplicationDetails {

  @Override
  default Integer getPillar() {
    return 2;
  }
}
