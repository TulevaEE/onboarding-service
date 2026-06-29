package ee.tuleva.onboarding.investment.report.publishing.data;

import ee.tuleva.onboarding.investment.instrument.InstrumentReference;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class InstrumentReferenceFixture {

  static InstrumentReference create(
      String isin, String displayName, String fundManager, String country) {
    var ref = BeanUtils.instantiateClass(InstrumentReference.class);
    ReflectionTestUtils.setField(ref, "isin", isin);
    ReflectionTestUtils.setField(ref, "displayName", displayName);
    ReflectionTestUtils.setField(ref, "fundManager", fundManager);
    ReflectionTestUtils.setField(ref, "country", country);
    return ref;
  }
}
