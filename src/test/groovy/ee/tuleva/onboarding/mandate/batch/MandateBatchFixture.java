package ee.tuleva.onboarding.mandate.batch;

import ee.tuleva.onboarding.mandate.Mandate;
import java.util.List;

public class MandateBatchFixture {

  public static MandateBatch.MandateBatchBuilder aMandateBatch() {
    return MandateBatch.builder().status(MandateBatchStatus.INITIALIZED);
  }

  public static MandateBatch aSavedMandateBatch(List<Mandate> mandates) {
    var batch = aMandateBatch().mandates(mandates).build();
    batch.setId(1L);

    return batch;
  }

  public static MandateBatch aSavedMandateBatch(List<Mandate> mandates, MandateBatchStatus status) {
    var batch = aMandateBatch().mandates(mandates).status(status).build();
    batch.setId(1L);
    return batch;
  }
}
