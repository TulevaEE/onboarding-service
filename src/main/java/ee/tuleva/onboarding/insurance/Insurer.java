package ee.tuleva.onboarding.insurance;

import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Insurer {
  COMPENSA("Compensa", "info@compensalife.ee"),
  ERGO("Ergo", "elukindlustus@ergo.ee"),
  ARTEA("Artea", "kindlustus@artea.ee");

  private final String displayName;
  @Getter private final String email;

  public String signedFileBaseName() {
    return "Tuleva_" + displayName + "_avaldus";
  }

  Map<String, Object> emailMergeVars() {
    return Map.of("insurerName", displayName, "insurerEmail", email);
  }
}
