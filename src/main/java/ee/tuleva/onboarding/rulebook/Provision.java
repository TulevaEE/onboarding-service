package ee.tuleva.onboarding.rulebook;

import java.util.List;

public sealed interface Provision permits Rule, Decision {

  String paragraph();

  String name();

  List<String> markdown();

  List<String> exampleNames();

  boolean decidesAsLabelled(String exampleName);

  default String heading() {
    return "p " + paragraph() + " – " + name();
  }
}
