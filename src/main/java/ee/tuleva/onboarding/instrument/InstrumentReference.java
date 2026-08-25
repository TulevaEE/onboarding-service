package ee.tuleva.onboarding.instrument;

import static jakarta.persistence.GenerationType.IDENTITY;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
@Entity
@Table(name = "instrument_reference")
public class InstrumentReference {

  @Id
  @GeneratedValue(strategy = IDENTITY)
  private @Nullable Long id;

  private String isin;
  private String displayName;
  private @Nullable String sebPositionName;
  private @Nullable String fundManager;
  private @Nullable String country;
  private @Nullable String instrumentType;
  private @Nullable String assetClass;
  private @Nullable String yahooTicker;
  private @Nullable String eodhdTicker;
  private @Nullable String bloombergTicker;
  private @Nullable String ric;
  private @Nullable String morningstarId;
  private @Nullable String blackrockProductId;
  private @Nullable String benchmarkCategory;
  private @Nullable LocalTime settlementCutoffTime;
  private @Nullable String settlementCutoffZone;
  private @Nullable Integer settlementDaysFromAcceptance;
  private boolean eodhdListed;
  private boolean active;
  private Instant createdAt;
  private Instant updatedAt;

  protected InstrumentReference() {}

  public boolean isExchangeTraded() {
    return eodhdTicker != null
        && (eodhdTicker.endsWith(".XETRA") || eodhdTicker.endsWith(".PA.EODHD"));
  }

  public boolean isListedOnEodhd() {
    return eodhdListed;
  }

  public Optional<String> getEodhdStorageKey() {
    if (isListedOnEodhd() && eodhdTicker != null) {
      return Optional.of(eodhdTicker);
    }
    return Optional.empty();
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

  public Optional<String> getExchangeStorageKey() {
    return getXetraStorageKey().or(this::getEuronextParisStorageKey);
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

  public Optional<SettlementTerms> settlementTerms() {
    if (settlementCutoffTime == null
        || settlementCutoffZone == null
        || settlementDaysFromAcceptance == null) {
      return Optional.empty();
    }
    return Optional.of(
        new SettlementTerms(
            settlementCutoffTime, ZoneId.of(settlementCutoffZone), settlementDaysFromAcceptance));
  }
}
