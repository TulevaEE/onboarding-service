package ee.tuleva.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularityTest {

  private final ApplicationModules modules =
      ApplicationModules.of(OnboardingServiceApplication.class);

  @Test
  @DisplayName("Detect modules and print structure")
  void detectModules() {
    modules.forEach(System.out::println);
  }

  @Test
  @Disabled("Enable after fixing module boundary violations")
  @DisplayName("Verify module structure")
  void verifyModuleStructure() {
    modules.verify();
  }

  @Test
  @DisplayName("Ledger module does not depend on domain modules")
  void ledgerDoesNotDependOnDomainModules() {
    // Ledger is core infrastructure - it should not depend on domain modules
    var forbiddenDependencies = Set.of("investment", "savings");

    var ledgerModule =
        modules.stream().filter(module -> module.getName().equals("ledger")).findFirst();

    assertThat(ledgerModule).isPresent();

    var ledgerDependencies =
        ledgerModule.get().getDependencies(modules).stream()
            .map(dep -> dep.getTargetModule().getName())
            .filter(forbiddenDependencies::contains)
            .toList();

    assertThat(ledgerDependencies)
        .as("Ledger module should not depend on domain modules")
        .isEmpty();
  }

  @Test
  @DisplayName("No other module depends on investment module")
  void noModuleDependsOnInvestment() {
    // savings is allowed to depend on investment because NAV calculation needs investment data
    var allowedDependents = Set.of("savings", "admin");

    var modulesWithInvestmentDependency =
        modules.stream()
            .filter(module -> !module.getName().equals("investment"))
            .filter(module -> !allowedDependents.contains(module.getName()))
            .filter(
                module ->
                    module.getDependencies(modules).stream()
                        .anyMatch(dep -> dep.getTargetModule().getName().equals("investment")))
            .map(ApplicationModule::getName)
            .toList();

    assertThat(modulesWithInvestmentDependency)
        .as("No module should depend on investment module")
        .isEmpty();
  }

  @Test
  @DisplayName("Create module documentation")
  void createModuleDocumentation() {
    new Documenter(modules).writeDocumentation();
  }
}
