package ee.tuleva.onboarding.savings.fund;

import static java.text.Normalizer.Form.NFKD;
import static java.util.stream.Collectors.joining;

import java.text.Normalizer;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class NameMatcher {

  private static final String FIE = "FIE";
  private static final String OSAUHING = "OSAUHING";
  private static final String OSAUHING_ABBREVIATION = "OU";

  public boolean isSameName(String name1, String name2) {
    if (name1 == null || name2 == null) return false;
    var normalized1 = normalize(name1);
    var normalized2 = normalize(name2);
    if (isMissingDistinguishingName(normalized1) || isMissingDistinguishingName(normalized2)) {
      return false;
    }
    return normalized1.equals(normalized2);
  }

  private boolean isMissingDistinguishingName(String normalizedName) {
    return normalizedName.isEmpty() || normalizedName.equals(OSAUHING_ABBREVIATION);
  }

  private String normalize(String name) {
    return Arrays.stream(splitIntoWords(name))
        .map(String::strip)
        .filter(token -> !token.isEmpty())
        .map(String::toUpperCase)
        .map(token -> Normalizer.normalize(token, NFKD).replaceAll("\\p{M}", ""))
        .filter(token -> !token.equals(FIE))
        .map(token -> token.equals(OSAUHING) ? OSAUHING_ABBREVIATION : token)
        .sorted()
        .collect(joining(" "));
  }

  private String[] splitIntoWords(String name) {
    return name.replaceAll("\\p{Cf}", "").replaceAll("[^\\p{L}\\p{N}\\p{M}]", " ").split("\\s+");
  }
}
