package ee.tuleva.onboarding.rulebook;

import java.util.List;

public record Phrase(String holds, String fails, List<String> notes) {

  public Phrase {
    if (holds.isBlank() || fails.isBlank()) {
      throw new IllegalArgumentException("Phrase is blank: holds=" + holds + ", fails=" + fails);
    }
    if (notes.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("Note is blank: holds=" + holds);
    }
    notes = List.copyOf(notes);
  }

  public Phrase(final String holds, final String fails, final String... notes) {
    this(holds, fails, List.of(notes));
  }

  String text(final Truth truth) {
    return switch (truth) {
      case HOLDS -> holds;
      case FAILS -> fails;
    };
  }
}
