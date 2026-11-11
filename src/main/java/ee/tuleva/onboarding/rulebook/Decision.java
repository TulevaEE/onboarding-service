package ee.tuleva.onboarding.rulebook;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record Decision<C, O>(
    String paragraph, String name, List<Branch<C, O>> branches, List<Case<C, O>> cases)
    implements Provision {

  public Decision {
    var ordered = List.copyOf(branches);
    branches = ordered;
    cases = List.copyOf(cases);
    var reached =
        cases.stream()
            .flatMap(example -> firstHolding(ordered, example.context()).stream())
            .distinct()
            .count();
    if (reached < branches.size()) {
      throw new IllegalArgumentException(
          "Decision needs an example reaching each branch: paragraph="
              + paragraph
              + ", branches="
              + branches.size()
              + ", reached="
              + reached);
    }
  }

  public O decide(final C context) {
    return branchFor(context).outcome();
  }

  @Override
  public List<String> markdown() {
    return Stream.of(
            Stream.of("## " + heading(), "Tulemus on esimene, mille tingimus kehtib:", outcomes()),
            data(),
            cases.stream().map(this::exampleText))
        .flatMap(paragraphs -> paragraphs)
        .toList();
  }

  @Override
  public List<String> exampleNames() {
    return cases.stream().map(Case::name).toList();
  }

  @Override
  public boolean decidesAsLabelled(final String exampleName) {
    return cases.stream()
        .filter(example -> example.name().equals(exampleName))
        .allMatch(example -> decide(example.context()).equals(example.expected()));
  }

  private static <C, O> Optional<Branch<C, O>> firstHolding(
      final List<Branch<C, O>> branches, final C context) {
    return branches.stream().filter(branch -> branch.when().test(context)).findFirst();
  }

  private Branch<C, O> branchFor(final C context) {
    return firstHolding(branches, context)
        .orElseThrow(
            () ->
                new IllegalStateException("No branch decides the context: paragraph=" + paragraph));
  }

  private String outcomes() {
    var lines =
        branches.stream()
            .map(branch -> "- " + branch.label() + ", kui " + branch.when().text(Truth.HOLDS))
            .toList();
    return String.join(";\n", lines) + ".";
  }

  private Stream<String> data() {
    var notes =
        branches.stream().flatMap(branch -> branch.when().notes().stream()).distinct().toList();
    return notes.isEmpty()
        ? Stream.empty()
        : Stream.of("Andmed: " + String.join("; ", notes) + ".");
  }

  private String exampleText(final Case<C, O> example) {
    var context = example.context();
    return "Näide: "
        + example.name()
        + " – "
        + branchFor(context).label()
        + ", sest "
        + because(context)
        + ".";
  }

  private String because(final C context) {
    var decisive = branchFor(context);
    var facts =
        branches.stream()
            .limit(branches.indexOf(decisive) + 1L)
            .flatMap(branch -> branch.when().facts(context).stream())
            .distinct()
            .map(fact -> fact.textOn(context))
            .toList();
    return Phrases.join(facts, "ja");
  }
}
