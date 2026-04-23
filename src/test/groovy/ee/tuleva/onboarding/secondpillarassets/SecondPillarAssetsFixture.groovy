package ee.tuleva.onboarding.secondpillarassets

class SecondPillarAssetsFixture {

  static SecondPillarAssets secondPillarAssetsFixture() {
    return new SecondPillarAssets(
        false,
        25000.00,
        10000.00,
        8000.00,
        500.00,
        12.34,
        0.00,
        0.00,
        0.00,
        0.00,
        0.00,
        0.00,
    )
  }

  static SecondPillarAssets secondPillarAssetsFixtureWithPik() {
    return new SecondPillarAssets(
        true,
        25000.00,
        10000.00,
        8000.00,
        500.00,
        12.34,
        0.00,
        0.00,
        0.00,
        0.00,
        0.00,
        0.00,
    )
  }
}
