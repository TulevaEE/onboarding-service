package ee.tuleva.onboarding;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModularityTest {

  private final ApplicationModules modules =
      ApplicationModules.of(OnboardingServiceApplication.class);

  @Test
  void verifyModuleStructure() {
    modules.verify();
  }

  @Test
  void ledgerDependsOnNothingButTheFundVocabulary() {
    // Ledger is core bookkeeping: the only outbound dependency it may have is the
    // TulevaFund vocabulary, and that one goes away once the enum gets its own module.
    var allowedDependencies = Set.of("fund");

    var ledgerModule = module("ledger");
    assertThat(ledgerModule).isPresent();

    var ledgerDependencies =
        ledgerModule.get().getDirectDependencies(modules).stream()
            .map(dep -> moduleName(dep.getTargetModule()))
            .filter(name -> !allowedDependencies.contains(name))
            .distinct()
            .toList();

    assertThat(ledgerDependencies)
        .as("Ledger must not gain outbound module dependencies")
        .isEmpty();
  }

  @Test
  void noModuleDependsOnInvestment() {
    // savings is allowed to depend on investment because NAV calculation needs investment data
    var allowedDependents = Set.of("savings", "admin");

    var modulesWithInvestmentDependency =
        modules.stream()
            .filter(module -> !moduleName(module).equals("investment"))
            .filter(module -> !allowedDependents.contains(moduleName(module)))
            .filter(
                module ->
                    module.getDirectDependencies(modules).stream()
                        .anyMatch(dep -> moduleName(dep.getTargetModule()).equals("investment")))
            .map(ModularityTest::moduleName)
            .toList();

    assertThat(modulesWithInvestmentDependency)
        .as("No module should depend on investment module")
        .isEmpty();
  }

  @Test
  void instrumentIsSharedAndMayBeDependedOnByAnyModule() {
    assertThat(module("instrument")).isPresent();

    var dependentsOnInstrument =
        modules.stream()
            .filter(module -> !moduleName(module).equals("instrument"))
            .filter(
                module ->
                    module.getDirectDependencies(modules).stream()
                        .anyMatch(dep -> moduleName(dep.getTargetModule()).equals("instrument")))
            .map(ModularityTest::moduleName)
            .toList();

    assertThat(dependentsOnInstrument)
        .as("Instrument is a shared module with no allowed-dependent restriction")
        .isNotEmpty();
  }

  @Test
  void instrumentDoesNotDependOnAnyOtherModule() {
    var instrumentModule = module("instrument");
    assertThat(instrumentModule).isPresent();

    var everyOtherModule =
        modules.stream()
            .map(ModularityTest::moduleName)
            .filter(name -> !name.equals("instrument"))
            .collect(toSet());

    var instrumentDependencies =
        instrumentModule.get().getDirectDependencies(modules).stream()
            .map(dep -> moduleName(dep.getTargetModule()))
            .filter(everyOtherModule::contains)
            .toList();

    assertThat(instrumentDependencies)
        .as("Instrument must stay dependency-free so every module can depend on it")
        .isEmpty();
  }

  private Optional<ApplicationModule> module(String name) {
    return modules.stream().filter(module -> moduleName(module).equals(name)).findFirst();
  }

  private static String moduleName(ApplicationModule module) {
    return module.getIdentifier().toString();
  }
}
