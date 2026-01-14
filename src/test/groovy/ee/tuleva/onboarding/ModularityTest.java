package ee.tuleva.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

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
  @DisplayName("No other module depends on investment module")
  void noModuleDependsOnInvestment() {
    var modulesWithInvestmentDependency =
        modules.stream()
            .filter(module -> !module.getName().equals("investment"))
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
