package ee.tuleva.onboarding.mandate.batch;

public class MandateBatchFixture {

  public static MandateBatch.MandateBatchBuilder aMandateBatch() {
    return MandateBatch.builder().status(MandateBatchStatus.INITIALIZED);
  }
}
