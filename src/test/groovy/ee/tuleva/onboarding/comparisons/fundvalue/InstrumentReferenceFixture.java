package ee.tuleva.onboarding.comparisons.fundvalue;

import ee.tuleva.onboarding.instrument.InstrumentReference;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

public class InstrumentReferenceFixture {

  private final Map<String, Object> fields = new LinkedHashMap<>();

  private InstrumentReferenceFixture(String isin) {
    fields.put("isin", isin);
    fields.put("displayName", isin);
    fields.put("active", true);
  }

  public static InstrumentReferenceFixture instrument(String isin) {
    return new InstrumentReferenceFixture(isin);
  }

  public InstrumentReferenceFixture displayName(String displayName) {
    fields.put("displayName", displayName);
    return this;
  }

  public InstrumentReferenceFixture yahooTicker(String yahooTicker) {
    fields.put("yahooTicker", yahooTicker);
    return this;
  }

  public InstrumentReferenceFixture eodhdTicker(String eodhdTicker) {
    fields.put("eodhdTicker", eodhdTicker);
    return this;
  }

  public InstrumentReferenceFixture bloombergTicker(String bloombergTicker) {
    fields.put("bloombergTicker", bloombergTicker);
    return this;
  }

  public InstrumentReferenceFixture morningstarId(String morningstarId) {
    fields.put("morningstarId", morningstarId);
    return this;
  }

  public InstrumentReferenceFixture blackrockProductId(String blackrockProductId) {
    fields.put("blackrockProductId", blackrockProductId);
    return this;
  }

  public InstrumentReferenceFixture eodhdListed(boolean eodhdListed) {
    fields.put("eodhdListed", eodhdListed);
    return this;
  }

  public InstrumentReferenceFixture active(boolean active) {
    fields.put("active", active);
    return this;
  }

  public InstrumentReference build() {
    var instrument = BeanUtils.instantiateClass(InstrumentReference.class);
    fields.forEach((name, value) -> ReflectionTestUtils.setField(instrument, name, value));
    return instrument;
  }
}
