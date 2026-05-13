package ee.tuleva.onboarding.investment.report.publishing.data;

import org.springframework.test.util.ReflectionTestUtils;

class InstrumentReferenceFixture {

  static InstrumentReference create(
      String isin, String displayName, String fundManager, String country) {
    var ref = new InstrumentReference();
    ReflectionTestUtils.setField(ref, "isin", isin);
    ReflectionTestUtils.setField(ref, "displayName", displayName);
    ReflectionTestUtils.setField(ref, "fundManager", fundManager);
    ReflectionTestUtils.setField(ref, "country", country);
    return ref;
  }
}
