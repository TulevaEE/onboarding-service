package ee.tuleva.onboarding;

import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import tools.jackson.databind.json.JsonMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ModuleMetricsTest {

  private static final Path BASELINE_FILE = Path.of("metrics/baseline.json");
  private static final Path OUTPUT_FILE = Path.of("build/metrics/modulith.json");
  private static final Path VIOLATIONS_FILE = Path.of("build/metrics/modulith-violations.txt");

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final ApplicationModules modules =
      ApplicationModules.of(OnboardingServiceApplication.class);

  private final List<String> violationMessages =
      modules.detectViolations().getMessages().stream().sorted().toList();

  @Test
  void emitsModuleMetrics() throws IOException {
    Map<String, TreeSet<String>> dependencies = directDependenciesByModule();
    Map<String, TreeSet<String>> dependents = invert(dependencies);
    List<List<String>> cycles = mutualCycles(dependencies);

    Map<String, Object> moduleMetrics = new TreeMap<>();
    for (String module : dependencies.keySet()) {
      int efferent = dependencies.get(module).size();
      int afferent = dependents.getOrDefault(module, new TreeSet<>()).size();
      double instability =
          efferent + afferent == 0 ? 0.0 : (double) efferent / (efferent + afferent);
      moduleMetrics.put(
          module,
          Map.of(
              "efferent", efferent,
              "afferent", afferent,
              "instability", Math.round(instability * 1000.0) / 1000.0));
    }

    Map<String, Object> metrics = new TreeMap<>();
    metrics.put("violationCount", violationMessages.size());
    metrics.put("cycleCount", cycles.size());
    metrics.put("cycles", cycles);
    metrics.put("modules", moduleMetrics);
    metrics.put("dependencies", dependencies);

    Files.createDirectories(OUTPUT_FILE.getParent());
    Files.writeString(
        OUTPUT_FILE, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metrics));
    Files.writeString(VIOLATIONS_FILE, String.join("\n", violationMessages));
  }

  @Test
  void moduleViolationsDoNotExceedRatchet() throws IOException {
    Map<?, ?> baseline = mapper.readValue(Files.readString(BASELINE_FILE), Map.class);
    int allowedViolations = ((Number) baseline.get("modulithViolations")).intValue();
    int allowedCycles = ((Number) baseline.get("moduleCycles")).intValue();

    assertThat(violationMessages.size())
        .as(
            "Modulith violations must never exceed the committed ratchet: baseline=%s",
            allowedViolations)
        .isLessThanOrEqualTo(allowedViolations);
    assertThat(mutualCycles(directDependenciesByModule()).size())
        .as("Module cycles must never exceed the committed ratchet: baseline=%s", allowedCycles)
        .isLessThanOrEqualTo(allowedCycles);
  }

  private Map<String, TreeSet<String>> directDependenciesByModule() {
    Map<String, TreeSet<String>> dependencies = new TreeMap<>();
    modules.forEach(
        module ->
            dependencies.put(
                moduleName(module),
                module.getDirectDependencies(modules).stream()
                    .map(dependency -> moduleName(dependency.getTargetModule()))
                    .collect(toCollection(TreeSet::new))));
    return dependencies;
  }

  private static Map<String, TreeSet<String>> invert(Map<String, TreeSet<String>> dependencies) {
    Map<String, TreeSet<String>> dependents = new TreeMap<>();
    dependencies.forEach(
        (source, targets) ->
            targets.forEach(
                target -> dependents.computeIfAbsent(target, name -> new TreeSet<>()).add(source)));
    return dependents;
  }

  private static List<List<String>> mutualCycles(Map<String, TreeSet<String>> dependencies) {
    return dependencies.entrySet().stream()
        .flatMap(
            entry ->
                entry.getValue().stream()
                    .filter(target -> entry.getKey().compareTo(target) < 0)
                    .filter(
                        target ->
                            dependencies
                                .getOrDefault(target, new TreeSet<>())
                                .contains(entry.getKey()))
                    .map(target -> List.of(entry.getKey(), target)))
        .toList();
  }

  private static String moduleName(ApplicationModule module) {
    return module.getIdentifier().toString();
  }
}
