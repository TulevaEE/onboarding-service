package ee.tuleva.onboarding.rulebook;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

public record Sisekord(
    int number, String title, String version, LocalDate validFrom, List<Rule<?>> rules) {

  public Sisekord {
    rules = List.copyOf(rules);
  }

  public String fileName() {
    return "sisekord-%02d.md".formatted(number);
  }

  public String markdown() {
    var blocks =
        Stream.of(
                Stream.of(
                    "# Sisekord " + number + ": " + title + " – reeglid",
                    "Reeglid järgivad sisekorra versiooni "
                        + version
                        + ", mis kehtib alates "
                        + Phrases.date(validFrom)
                        + ".",
                    "Tekst on genereeritud reeglite koodist ja iga näide on automaattest. Faili"
                        + " käsitsi ei muudeta.",
                    "## Kaetus",
                    "Kood rakendab järgmisi sisekorra punkte; ülejäänud punktid on sisekorra"
                        + " tekstis.",
                    coverage()),
                rules.stream().flatMap(rule -> rule.markdown().stream()))
            .flatMap(block -> block)
            .toList();
    return String.join("\n\n", blocks) + "\n";
  }

  private String coverage() {
    return String.join("\n", rules.stream().map(rule -> "- " + rule.heading()).toList());
  }
}
