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
  void ledgerDoesNotDependOnDomainModules() {
    // Ledger is core infrastructure - it should not depend on domain modules
    var forbiddenDependencies = Set.of("investment", "savings");

    var ledgerModule =
        modules.stream().filter(module -> moduleName(module).equals("ledger")).findFirst();

    assertThat(ledgerModule).isPresent();

    var ledgerDependencies =
        ledgerModule.get().getDirectDependencies(modules).stream()
            .map(dep -> moduleName(dep.getTargetModule()))
            .filter(forbiddenDependencies::contains)
            .toList();

    assertThat(ledgerDependencies)
        .as("Ledger module should not depend on domain modules")
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
