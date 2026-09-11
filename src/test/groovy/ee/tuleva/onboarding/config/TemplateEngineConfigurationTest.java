package ee.tuleva.onboarding.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.thymeleaf.templatemode.TemplateMode.HTML;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

class TemplateEngineConfigurationTest {

  private final TemplateEngineConfiguration configuration = new TemplateEngineConfiguration();

  @Test
  void templateResolverRequiresAnApplicationContextToHaveBeenSet() {
    assertThatThrownBy(configuration::templateResolver).isInstanceOf(NullPointerException.class);
  }

  @Test
  void templateResolverResolvesCacheableHtmlTemplatesFromTheClasspath() throws Exception {
    ApplicationContext applicationContext = mock(ApplicationContext.class);
    configuration.setApplicationContext(applicationContext);

    ITemplateResolver resolver = configuration.templateResolver();

    assertThat(resolver).isInstanceOf(SpringResourceTemplateResolver.class);
    var springResolver = (SpringResourceTemplateResolver) resolver;
    assertThat(springResolver.getPrefix()).isEqualTo("classpath:/templates/");
    assertThat(springResolver.getSuffix()).isEqualTo(".html");
    assertThat(springResolver.getTemplateMode()).isEqualTo(HTML);
    assertThat(springResolver.getCharacterEncoding()).isEqualTo("UTF-8");
    assertThat(springResolver.isCacheable()).isTrue();
    Field field = SpringResourceTemplateResolver.class.getDeclaredField("applicationContext");
    field.setAccessible(true);
    assertThat(field.get(springResolver)).isSameAs(applicationContext);
  }

  @Test
  void templateEngineUsesTheProvidedResolverAndEnablesTheSpringElCompiler() {
    configuration.setApplicationContext(mock(ApplicationContext.class));
    ITemplateResolver resolver = configuration.templateResolver();

    SpringTemplateEngine engine = configuration.templateEngine(resolver);

    assertThat(engine).isNotNull();
    assertThat(engine.getTemplateResolvers()).containsExactly(resolver);
    assertThat(engine.getEnableSpringELCompiler()).isTrue();
  }
}
