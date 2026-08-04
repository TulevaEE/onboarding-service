package ee.tuleva.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModularityTest {

  private final ApplicationModules modules =
      ApplicationModules.of(OnboardingServiceApplication.class);

  @Test
  @Disabled
  void detectModules() {
    modules.forEach(System.out::println);
  }

  @Test
  @Disabled("Enable after fixing module boundary violations")
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

  private static String moduleName(ApplicationModule module) {
    return module.getIdentifier().toString();
  }
}
