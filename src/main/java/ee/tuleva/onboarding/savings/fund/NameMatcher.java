package ee.tuleva.onboarding.savings.fund;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class NameMatcher {

  public boolean isSameName(String name1, String name2) {
    if (name1 == null || name2 == null) return false;
    return normalize(name1.strip()).equals(normalize(name2.strip()));
  }

  private String normalize(String name) {
    return Arrays.stream(name.replaceAll("\\p{Punct}", " ").split("\\s+"))
        .map(String::strip)
        .map(String::toUpperCase)
        .map(s -> Normalizer.normalize(s, Normalizer.Form.NFKD).replaceAll("\\p{M}", ""))
        .sorted()
        .collect(Collectors.joining(" "));
  }
}
