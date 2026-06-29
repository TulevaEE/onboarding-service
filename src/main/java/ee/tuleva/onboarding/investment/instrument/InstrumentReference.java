package ee.tuleva.onboarding.investment.instrument;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import lombok.Getter;

@Getter
@Entity
@Table(name = "instrument_reference")
public class InstrumentReference {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private Long id;

  private String isin;
  private String displayName;
  private String sebPositionName;
  private String fundManager;
  private String country;
  private String instrumentType;
  private String assetClass;
  private String yahooTicker;
  private String eodhdTicker;
  private String bloombergTicker;
  private String ric;
  private String morningstarId;
  private String blackrockProductId;
  private String benchmarkCategory;
  private Boolean eodhdListed;
  private boolean active;
  private Instant createdAt;
  private Instant updatedAt;

  protected InstrumentReference() {}

  public boolean isExchangeTraded() {
    return eodhdTicker != null
        && (eodhdTicker.endsWith(".XETRA") || eodhdTicker.endsWith(".PA.EODHD"));
  }

  public boolean isListedOnEodhd() {
    return !Boolean.FALSE.equals(eodhdListed);
  }

  public Optional<String> getXetraStorageKey() {
    if (eodhdTicker != null && eodhdTicker.endsWith(".XETRA")) {
      return Optional.of(isin + ".XETR");
    }
    return Optional.empty();
  }

  public Optional<String> getEuronextParisStorageKey() {
    if (eodhdTicker != null && eodhdTicker.endsWith(".PA.EODHD")) {
      return Optional.of(isin + ".XPAR");
    }
    return Optional.empty();
  }

  public Optional<String> getBlackrockStorageKey() {
    if (blackrockProductId != null) {
      return Optional.of(isin + ".BLACKROCK");
    }
    return Optional.empty();
  }

  public Optional<String> getMorningstarStorageKey() {
    if (morningstarId != null) {
      return Optional.of(isin + ".MORNINGSTAR");
    }
    return Optional.empty();
  }

  public String getEffectiveDisplayName() {
    return sebPositionName != null ? sebPositionName : displayName;
  }
}
