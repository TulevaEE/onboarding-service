package ee.tuleva.onboarding.instrument;

import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

public class InstrumentReferenceFixture {

  private final InstrumentReference instrument =
      BeanUtils.instantiateClass(InstrumentReference.class);

  public InstrumentReferenceFixture() {
    eodhdListed(true);
  }

  public static InstrumentReferenceFixture anInstrument() {
    return new InstrumentReferenceFixture();
  }

  public static InstrumentReferenceFixture instrument(String isin) {
    return anInstrument().isin(isin).displayName(isin).active(true);
  }

  public static InstrumentReference create(
      String isin, String displayName, String fundManager, String country) {
    return anInstrument()
        .isin(isin)
        .displayName(displayName)
        .fundManager(fundManager)
        .country(country)
        .build();
  }

  public InstrumentReferenceFixture isin(String isin) {
    return set("isin", isin);
  }

  public InstrumentReferenceFixture displayName(String displayName) {
    return set("displayName", displayName);
  }

  public InstrumentReferenceFixture sebPositionName(String sebPositionName) {
    return set("sebPositionName", sebPositionName);
  }

  public InstrumentReferenceFixture fundManager(String fundManager) {
    return set("fundManager", fundManager);
  }

  public InstrumentReferenceFixture country(String country) {
    return set("country", country);
  }

  public InstrumentReferenceFixture yahooTicker(String yahooTicker) {
    return set("yahooTicker", yahooTicker);
  }

  public InstrumentReferenceFixture eodhdTicker(String eodhdTicker) {
    return set("eodhdTicker", eodhdTicker);
  }

  public InstrumentReferenceFixture bloombergTicker(String bloombergTicker) {
    return set("bloombergTicker", bloombergTicker);
  }

  public InstrumentReferenceFixture blackrockProductId(String blackrockProductId) {
    return set("blackrockProductId", blackrockProductId);
  }

  public InstrumentReferenceFixture morningstarId(String morningstarId) {
    return set("morningstarId", morningstarId);
  }

  public InstrumentReferenceFixture benchmarkCategory(String benchmarkCategory) {
    return set("benchmarkCategory", benchmarkCategory);
  }

  public InstrumentReferenceFixture eodhdListed(boolean eodhdListed) {
    return set("eodhdListed", eodhdListed);
  }

  public InstrumentReferenceFixture active(boolean active) {
    return set("active", active);
  }

  public InstrumentReference build() {
    return instrument;
  }

  private InstrumentReferenceFixture set(String fieldName, Object value) {
    ReflectionTestUtils.setField(instrument, fieldName, value);
    return this;
  }
}
