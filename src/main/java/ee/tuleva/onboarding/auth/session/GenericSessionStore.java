package ee.tuleva.onboarding.auth.session;

import ee.tuleva.onboarding.auth.principal.AuthenticationHolder;
import java.io.*;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GenericSessionStore {

  private final SessionAttributeRepository repository;
  private final AuthenticationHolder authenticationHolder;

  public <T extends Serializable> void save(T sessionAttribute) {
    Long userId = authenticationHolder.getAuthenticatedPerson().getUserId();
    String attributeName = sessionAttribute.getClass().getName();
    byte[] attributeBytes;

    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(bos)) {

      out.writeObject(sessionAttribute);
      attributeBytes = bos.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Error serializing session attribute", e);
    }

    repository.findByUserIdAndAttributeName(userId, attributeName).ifPresent(repository::delete);

    SessionAttribute newAttribute =
        SessionAttribute.builder()
            .userId(userId)
            .attributeName(attributeName)
            .attributeValue(attributeBytes)
            .build();
    repository.save(newAttribute);
  }

  public <T extends Serializable> Optional<T> get(Class<T> clazz) {
    Long userId = authenticationHolder.getAuthenticatedPerson().getUserId();
    return repository
        .findByUserIdAndAttributeName(userId, clazz.getName())
        .flatMap(
            attribute -> {
              try (ObjectInput in =
                  new ObjectInputStream(new ByteArrayInputStream(attribute.getAttributeValue()))) {
                @SuppressWarnings("unchecked")
                T result = (T) in.readObject();
                return Optional.of(result);
              } catch (IOException | ClassNotFoundException e) {
                return Optional.empty();
              }
            });
  }
}
