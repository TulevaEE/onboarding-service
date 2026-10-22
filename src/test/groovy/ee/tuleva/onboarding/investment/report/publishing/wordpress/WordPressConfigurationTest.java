package ee.tuleva.onboarding.investment.report.publishing.wordpress;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WordPressConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(WordPressConfiguration.class);

  @Test
  void contextStartsWhenPublishingIsEnabledWithoutWordPressConfiguration() {
    contextRunner
        .withPropertyValues("investment-report-publishing.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(WordPressMediaClient.class);
            });
  }

  @Test
  void publishingRefusesToRunWhenNoWordPressPropertyIsConfigured() {
    contextRunner
        .withPropertyValues("investment-report-publishing.enabled=true")
        .run(
            context ->
                assertThatThrownBy(
                        () ->
                            context
                                .getBean(WordPressMediaClient.class)
                                .updateAcfReportField("any-slug", 42))
                    .isInstanceOf(IllegalStateException.class));
  }

  @Test
  void uploadRefusesToRunWhenNoWordPressPropertyIsConfigured() {
    contextRunner
        .withPropertyValues("investment-report-publishing.enabled=true")
        .run(
            context ->
                assertThatThrownBy(
                        () ->
                            context
                                .getBean(WordPressMediaClient.class)
                                .upload("report.pdf", new byte[] {1}))
                    .isInstanceOf(IllegalStateException.class));
  }

  @Test
  void publishingRefusesToRunWhenTheAppPasswordIsBlank() {
    contextRunner
        .withPropertyValues(
            "investment-report-publishing.enabled=true",
            "investment-report-publishing.wordpress.api-base=https://example.test/wp-json/wp/v2",
            "investment-report-publishing.wordpress.username=wp-user",
            "investment-report-publishing.wordpress.app-password=")
        .run(
            context ->
                assertThatThrownBy(
                        () ->
                            context
                                .getBean(WordPressMediaClient.class)
                                .updateAcfReportField("any-slug", 42))
                    .isInstanceOf(IllegalStateException.class));
  }

  @Test
  void wordPressClientIsCreatedWhenAllPropertiesArePresent() {
    contextRunner
        .withPropertyValues(
            "investment-report-publishing.enabled=true",
            "investment-report-publishing.wordpress.api-base=https://example.test/wp-json/wp/v2",
            "investment-report-publishing.wordpress.username=wp-user",
            "investment-report-publishing.wordpress.app-password=wp-password")
        .run(context -> assertThat(context).hasSingleBean(WordPressMediaClient.class));
  }

  @Test
  void contextStartsWithOnlyThePageReaderWhenPublishingIsDisabled() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(WordPressMediaClient.class);
          assertThat(context).hasSingleBean(WordPressPageReader.class);
        });
  }

  @Test
  void pageReaderSendsNoCredentialsEvenWhenPublishingIsConfigured() throws Exception {
    List<Boolean> requestsCarryingCredentials = new CopyOnWriteArrayList<>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requestsCarryingCredentials.add(
              exchange.getRequestHeaders().containsKey("Authorization"));
          var body = "[{\"id\": 17533, \"acf\": {\"investment_report_file\": null}}]";
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.getBytes(UTF_8).length);
          exchange.getResponseBody().write(body.getBytes(UTF_8));
          exchange.close();
        });
    server.start();
    try {
      contextRunner
          .withPropertyValues(
              "investment-report-publishing.enabled=true",
              "investment-report-publishing.wordpress.api-base=http://127.0.0.1:"
                  + server.getAddress().getPort(),
              "investment-report-publishing.wordpress.username=wp-user",
              "investment-report-publishing.wordpress.app-password=wp-password")
          .run(
              context ->
                  assertThat(
                          context
                              .getBean(WordPressPageReader.class)
                              .investmentReportUrl("tuleva-maailma-aktsiate-pensionifond"))
                      .isEmpty());
    } finally {
      server.stop(0);
    }

    assertThat(requestsCarryingCredentials).containsExactly(false);
  }
}
