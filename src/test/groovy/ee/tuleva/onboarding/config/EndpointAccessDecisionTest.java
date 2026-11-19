package ee.tuleva.onboarding.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

class EndpointAccessDecisionTest {

  private static final String PINNED_DECISIONS = "endpoint-access-decisions.txt";

  @Test
  void everyEndpointTheApplicationServesHasItsAccessDecisionPinned() throws IOException {
    assertThat(servedEndpoints())
        .containsExactlyInAnyOrderElementsOf(
            pinnedDecisions().stream().map(PinnedDecision::endpoint).toList());
  }

  private enum WhoMayCall {
    ANYONE,
    ADMIN_TOKEN_HOLDER,
    LOGGED_IN_CALLER
  }

  private record PinnedDecision(WhoMayCall whoMayCall, String endpoint) {
    static PinnedDecision parse(String line) {
      var decisionAndEndpoint = line.split(" ", 2);
      return new PinnedDecision(WhoMayCall.valueOf(decisionAndEndpoint[0]), decisionAndEndpoint[1]);
    }
  }

  private static List<PinnedDecision> pinnedDecisions() throws IOException {
    return new ClassPathResource(PINNED_DECISIONS)
        .getContentAsString(UTF_8)
        .lines()
        .filter(line -> !line.isBlank())
        .map(PinnedDecision::parse)
        .toList();
  }

  private static List<String> servedEndpoints() throws IOException {
    var resolver = new PathMatchingResourcePatternResolver();
    var metadataReaders = new CachingMetadataReaderFactory(resolver);
    var endpoints = new TreeSet<String>();
    for (var classFile : resolver.getResources("classpath*:ee/tuleva/onboarding/**/*.class")) {
      var metadata = metadataReaders.getMetadataReader(classFile).getAnnotationMetadata();
      if (isProductionCode(classFile) && metadata.isAnnotated(Controller.class.getName())) {
        endpointsOf(controllerClass(metadata.getClassName())).forEach(endpoints::add);
      }
    }
    return List.copyOf(endpoints);
  }

  private static boolean isProductionCode(Resource classFile) throws IOException {
    return !classFile.getURL().getPath().contains("/test/");
  }

  private static Class<?> controllerClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Controller class not loadable: className=" + className, e);
    }
  }

  private static Stream<String> endpointsOf(Class<?> controller) {
    var classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
    var prefixes = classMapping == null ? new String[] {""} : orEmptyPath(classMapping.path());
    return MethodIntrospector.selectMethods(
            controller,
            (Method method) ->
                AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
        .values()
        .stream()
        .flatMap(
            mapping ->
                Arrays.stream(prefixes)
                    .flatMap(
                        prefix ->
                            Arrays.stream(orEmptyPath(mapping.path()))
                                .flatMap(
                                    path ->
                                        httpMethodsOf(mapping)
                                            .map(method -> method + " " + prefix + path))));
  }

  private static String[] orEmptyPath(String[] paths) {
    return paths.length == 0 ? new String[] {""} : paths;
  }

  private static Stream<String> httpMethodsOf(RequestMapping mapping) {
    return mapping.method().length == 0
        ? Stream.of("ANY")
        : Arrays.stream(mapping.method()).map(Enum::name);
  }
}
