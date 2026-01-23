package ee.tuleva.onboarding.savings.fund;

import static java.util.stream.Collectors.joining;

import java.text.Normalizer;
import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class NameMatcher {

  public boolean isSameName(String name1, String name2) {
    if (name1 == null || name2 == null) return false;
    return normalize(stripFie(name1)).equals(normalize(stripFie(name2)));
  }

  private String stripFie(String name) {
    return name.replaceAll("(?i)\\bFIE\\b", "").strip();
  }

  private String normalize(String name) {
    return Arrays.stream(name.replaceAll("\\p{Punct}", " ").split("\\s+"))
        .map(String::strip)
        .map(String::toUpperCase)
        .map(s -> Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", ""))
        .sorted()
        .collect(joining(" "));
  }
}
