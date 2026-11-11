package ee.tuleva.onboarding.rulebook;

import java.util.List;
import java.util.stream.Stream;

public record Rule<C>(
    String paragraph, String name, Condition<C> condition, List<Example<C>> examples)
    implements Provision {

  public Rule {
    examples = List.copyOf(examples);
    var labels = examples.stream().map(Example::labelled).distinct().count();
    if (labels < Truth.values().length) {
      throw new IllegalArgumentException(
          "Rule needs an example of each outcome: paragraph="
              + paragraph
              + ", examples="
              + examples.size());
    }
  }

  private Truth on(final C context) {
    return Truth.of(condition.test(context));
  }

  public boolean appliesTo(final C context) {
    return condition.test(context);
  }

  public boolean decidesAsLabelled(final Example<C> example) {
    return on(example.context()) == example.labelled();
  }

  public List<Example<C>> mislabelled() {
    return examples.stream().filter(example -> !decidesAsLabelled(example)).toList();
  }

  @Override
  public List<String> exampleNames() {
    return examples.stream().map(Example::name).toList();
  }

  @Override
  public boolean decidesAsLabelled(final String exampleName) {
    return examples.stream()
        .filter(example -> example.name().equals(exampleName))
        .allMatch(this::decidesAsLabelled);
  }

  @Override
  public List<String> markdown() {
    return Stream.of(
            Stream.of("## " + heading(), "Rakendub, kui " + condition.text(Truth.HOLDS) + "."),
            data(),
            examples.stream().map(this::exampleText))
        .flatMap(paragraphs -> paragraphs)
        .toList();
  }

  private Stream<String> data() {
    var notes = condition.notes();
    return notes.isEmpty()
        ? Stream.empty()
        : Stream.of("Andmed: " + String.join("; ", notes) + ".");
  }

  private String exampleText(final Example<C> example) {
    var context = example.context();
    return "Näide: "
        + example.name()
        + " – "
        + outcome(on(context))
        + ", sest "
        + because(context)
        + ".";
  }

  private static String outcome(final Truth truth) {
    return switch (truth) {
      case HOLDS -> "rakendub";
      case FAILS -> "ei rakendu";
    };
  }

  private String because(final C context) {
    var facts = condition.facts(context);
    return Phrases.join(facts.stream().map(fact -> fact.textOn(context)).toList(), "ja");
  }
}
