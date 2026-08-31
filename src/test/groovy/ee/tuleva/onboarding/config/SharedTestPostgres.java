package ee.tuleva.onboarding.config;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.springframework.core.env.Environment;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

final class SharedTestPostgres {

  private static final String WORKER_ID = System.getProperty("org.gradle.test.worker", "0");
  private static final Map<String, String> TEMPLATES = new ConcurrentHashMap<>();
  private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();

  private SharedTestPostgres() {}

  static TestDatabase createDatabase(Environment environment) {
    Bootstrap bootstrap = bootstrap(environment);
    String template = template(bootstrap, environment);
    String database = "onboarding_test_w" + WORKER_ID + "_" + DATABASE_SEQUENCE.incrementAndGet();
    execute(bootstrap, "CREATE DATABASE " + database + " TEMPLATE " + template);
    return new TestDatabase(bootstrap.urlFor(database), bootstrap.username(), bootstrap.password());
  }

  record TestDatabase(String url, String username, String password) {}

  // Both CI worker JVMs share one PostgreSQL server, so the template name has to be per worker:
  // a single shared name would let one worker clone a template the other is still migrating.
  private static String template(Bootstrap bootstrap, Environment environment) {
    return TEMPLATES.computeIfAbsent(
        "onboarding_test_w" + WORKER_ID + "_template",
        name -> {
          // A template build that fails midway leaves the database behind but caches no mapping,
          // so dropping first keeps the retry self-healing instead of failing every later context.
          execute(bootstrap, "DROP DATABASE IF EXISTS " + name);
          execute(bootstrap, "CREATE DATABASE " + name);
          Flyway.configure(SharedTestPostgres.class.getClassLoader())
              .dataSource(bootstrap.urlFor(name), bootstrap.username(), bootstrap.password())
              .locations(environment.getRequiredProperty("spring.flyway.locations", String[].class))
              .load()
              .migrate();
          terminateConnectionsTo(bootstrap, name);
          return name;
        });
  }

  // CREATE DATABASE ... TEMPLATE refuses to run while anything is connected to the template, and
  // a just-closed JDBC connection can outlive close() by a moment on the server side.
  private static void terminateConnectionsTo(Bootstrap bootstrap, String database) {
    execute(
        bootstrap,
        "SELECT pg_terminate_backend(pid, 10000) FROM pg_stat_activity"
            + " WHERE datname = '"
            + database
            + "' AND pid <> pg_backend_pid()");
  }

  private static Bootstrap bootstrap(Environment environment) {
    if (Arrays.asList(environment.getActiveProfiles()).contains("pg")) {
      PostgreSQLContainer container = Container.INSTANCE;
      return new Bootstrap(
          container.getHost(),
          container.getFirstMappedPort(),
          container.getDatabaseName(),
          container.getUsername(),
          container.getPassword());
    }
    URI url =
        URI.create(
            environment.getRequiredProperty("spring.datasource.url").substring("jdbc:".length()));
    return new Bootstrap(
        url.getHost(),
        url.getPort(),
        url.getPath().substring(1),
        environment.getRequiredProperty("spring.datasource.username"),
        environment.getRequiredProperty("spring.datasource.password"));
  }

  private static void execute(Bootstrap bootstrap, String sql) {
    try (Connection connection =
            DriverManager.getConnection(
                bootstrap.url(), bootstrap.username(), bootstrap.password());
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException e) {
      throw new IllegalStateException("Test database statement failed: sql=" + sql, e);
    }
  }

  private record Bootstrap(
      String host, int port, String database, String username, String password) {

    String url() {
      return urlFor(database);
    }

    String urlFor(String name) {
      return "jdbc:postgresql://" + host + ":" + port + "/" + name;
    }
  }

  private static final class Container {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer INSTANCE =
        new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
            .withCommand(
                "postgres", "-c", "timezone=UTC", "-c", "fsync=off", "-c", "max_connections=300");

    static {
      INSTANCE.start();
    }
  }
}
