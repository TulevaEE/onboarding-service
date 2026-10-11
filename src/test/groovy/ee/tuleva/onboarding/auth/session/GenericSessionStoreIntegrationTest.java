package ee.tuleva.onboarding.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@JdbcTest
class GenericSessionStoreIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JdbcClient jdbcClient;
  @Autowired private PlatformTransactionManager transactionManager;

  private JdbcIndexedSessionRepository sessionRepository;
  private SessionRepositoryFilter<?> sessionRepositoryFilter;
  private GenericSessionStore sessionStore;

  @BeforeEach
  void setUp() {
    sessionRepository =
        new JdbcIndexedSessionRepository(jdbcTemplate, new TransactionTemplate(transactionManager));
    sessionRepositoryFilter = new SessionRepositoryFilter<>(sessionRepository);
    sessionStore = new GenericSessionStore(sessionRepository);
  }

  @Test
  void readsAnAttributeItSavedInAnEarlierRequestOfTheSameSession() throws Exception {
    String sessionId = sessionHolding(new ArrayList<>(List.of("in flight")));

    var attribute = inRequestWithSessionCookie(sessionId, () -> sessionStore.get(ArrayList.class));

    assertThat(attribute).contains(new ArrayList<>(List.of("in flight")));
  }

  @Test
  void dropsTheSessionOfAnAttributeStoredByAnotherVersionOfItsClassAndReadsItAsAbsent()
      throws Exception {
    String sessionId = sessionHolding(new ArrayList<>(List.of("in flight")));
    storeAsAnotherVersionOfItsClass(sessionId, new ArrayList<>(List.of("in flight")));

    var attribute = inRequestWithSessionCookie(sessionId, () -> sessionStore.get(ArrayList.class));

    assertThat(attribute).isEmpty();
    assertThat(sessionRepository.findById(sessionId)).isNull();
  }

  @Test
  void theRequestAfterAnAttributeStoredByAnotherVersionOfItsClassStartsAFreshSession()
      throws Exception {
    String sessionId = sessionHolding(new ArrayList<>(List.of("in flight")));
    storeAsAnotherVersionOfItsClass(sessionId, new ArrayList<>(List.of("in flight")));
    inRequestWithSessionCookie(sessionId, () -> sessionStore.get(ArrayList.class));

    var attribute =
        inRequestWithSessionCookie(
            sessionId,
            () -> {
              sessionStore.save(new ArrayList<>(List.of("next login")));
              return sessionStore.get(ArrayList.class);
            });

    assertThat(attribute).contains(new ArrayList<>(List.of("next login")));
  }

  private String sessionHolding(ArrayList<String> attribute) {
    return savedSessionHolding(sessionRepository, attribute);
  }

  private static <S extends Session> String savedSessionHolding(
      SessionRepository<S> repository, ArrayList<String> attribute) {
    S session = repository.createSession();
    session.setAttribute(ArrayList.class.getName(), attribute);
    repository.save(session);
    return session.getId();
  }

  private void storeAsAnotherVersionOfItsClass(String sessionId, Serializable attribute) {
    jdbcClient
        .sql(
            """
            UPDATE spring_session_attributes SET attribute_bytes = :bytes
            WHERE attribute_name = :name
              AND session_primary_id = (SELECT primary_id FROM spring_session WHERE session_id = :id)
            """)
        .param("bytes", serializedWithAnotherSerialVersionUid(attribute))
        .param("name", attribute.getClass().getName())
        .param("id", sessionId)
        .update();
  }

  private static byte[] serializedWithAnotherSerialVersionUid(Serializable value) {
    byte[] bytes = new SerializingConverter().convert(value);
    byte[] serialVersionUid =
        ByteBuffer.allocate(Long.BYTES)
            .putLong(ObjectStreamClass.lookup(value.getClass()).getSerialVersionUID())
            .array();
    int at = Collections.indexOfSubList(boxed(bytes), boxed(serialVersionUid));
    bytes[at + Long.BYTES - 1] ^= 1;
    return bytes;
  }

  private static List<Byte> boxed(byte[] bytes) {
    return IntStream.range(0, bytes.length).mapToObj(i -> bytes[i]).toList();
  }

  private <T> Optional<T> inRequestWithSessionCookie(String sessionId, Supplier<Optional<T>> action)
      throws Exception {
    var request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("SESSION", Base64.getEncoder().encodeToString(sessionId.getBytes())));
    var result = new AtomicReference<Optional<T>>();
    sessionRepositoryFilter.doFilter(
        request,
        new MockHttpServletResponse(),
        (wrappedRequest, response) -> {
          RequestContextHolder.setRequestAttributes(
              new ServletRequestAttributes((HttpServletRequest) wrappedRequest));
          try {
            result.set(action.get());
          } finally {
            RequestContextHolder.resetRequestAttributes();
          }
        });
    return result.get();
  }
}
