package ee.tuleva.onboarding.kyb.screener;

import jakarta.annotation.Nullable;
import java.util.Locale;
import java.util.stream.Stream;

enum EstonianCounty {
  HARJU("Harju"),
  HIIU("Hiiu"),
  IDA_VIRU("Ida-Viru"),
  JOGEVA("Jõgeva"),
  JARVA("Järva"),
  LAANE("Lääne"),
  LAANE_VIRU("Lääne-Viru"),
  POLVA("Põlva"),
  PARNU("Pärnu"),
  RAPLA("Rapla"),
  SAARE("Saare"),
  TARTU("Tartu"),
  VALGA("Valga"),
  VILJANDI("Viljandi"),
  VORU("Võru");

  private final String stem;

  EstonianCounty(String stem) {
    this.stem = stem;
  }

  static boolean isPresentIn(@Nullable String address) {
    if (address == null || address.isBlank()) {
      return false;
    }
    var normalized = address.toLowerCase(Locale.ROOT);
    return Stream.of(values()).anyMatch(county -> county.matches(normalized));
  }

  private boolean matches(String normalizedAddress) {
    var lowerStem = stem.toLowerCase(Locale.ROOT);
    return normalizedAddress.contains(lowerStem + " maakond")
        || normalizedAddress.contains(lowerStem + "maa");
  }
}
