package ee.tuleva.onboarding.auth.session

import spock.lang.Specification
import ee.tuleva.onboarding.auth.principal.AuthenticationHolder
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared

import static ee.tuleva.onboarding.auth.AuthenticatedPersonFixture.sampleAuthenticatedPersonAndMember

class GenericSessionStoreSpec extends Specification {

  @Shared
  @Autowired
  GenericSessionStore sessionStore

  def sessionAttributeRepository = Mock(SessionAttributeRepository)
  def authenticationHolder = Mock(AuthenticationHolder)
  def person = sampleAuthenticatedPersonAndMember().build()

  def setup() {
    sessionStore = new GenericSessionStore(sessionAttributeRepository, authenticationHolder)
  }

  def "save serializable session attribute"() {
    given: "A serializable session attribute"
        Serializable testAttribute = "TestAttribute"
        String attributeName = testAttribute.getClass().getName()
        byte[] serializedAttribute = serialize(testAttribute)

        authenticationHolder.getAuthenticatedPerson() >> person

    when: "save is called with the session attribute"
        sessionStore.save(testAttribute)

    then: "The repository deletes any existing attribute and saves the new one"
        1 * sessionAttributeRepository.findByUserIdAndAttributeName(person.userId, attributeName) >> Optional.empty()
        1 * sessionAttributeRepository.save(_ as SessionAttribute) >> { SessionAttribute attribute ->
          assert attribute.getUserId() == person.userId
          assert attribute.getAttributeName() == attributeName
          assert Arrays.equals(attribute.getAttributeValue(), serializedAttribute)
        }
  }

  def "retrieve serializable session attribute"() {
    given: "A stored session attribute"
        Serializable testAttribute = "TestAttribute"
        byte[] serializedAttribute = serialize(testAttribute)
        SessionAttribute storedAttribute = SessionAttribute.builder()
            .userId(person.userId)
            .attributeName("java.lang.String")
            .attributeValue(serializedAttribute)
            .build()

        authenticationHolder.getAuthenticatedPerson() >> person
        sessionAttributeRepository.findByUserIdAndAttributeName(person.userId, "java.lang.String") >> Optional.of(storedAttribute)

    when: "get is called with the attribute class"
        Optional<String> result = sessionStore.get(String.class)

    then: "The attribute is retrieved successfully"
        result.isPresent()
        result.get() == testAttribute
  }

  def "handle serialization error on save"() {
    given: "A non-serializable session attribute"
        Object testAttribute = new Object()

        authenticationHolder.getAuthenticatedPerson() >> person

    when: "save is called with the non-serializable session attribute"
        sessionStore.save(testAttribute)

    then: "A RuntimeException is thrown"
        thrown(RuntimeException)
  }

  def "return empty Optional when attribute not found on get"() {
    given: "An attribute class name"
        Class<String> attributeClass = String.class

        authenticationHolder.getAuthenticatedPerson() >> person
        sessionAttributeRepository.findByUserIdAndAttributeName(person.userId, attributeClass.getName()) >> Optional.empty()

    when: "get is called with the attribute class"
        Optional<String> result = sessionStore.get(attributeClass)

    then: "No attribute is found and an empty Optional is returned"
        !result.isPresent()
  }

  private static byte[] serialize(Serializable object) {
    ByteArrayOutputStream bos = new ByteArrayOutputStream()
    ObjectOutputStream oos = new ObjectOutputStream(bos)
    oos.writeObject(object)
    oos.flush()
    return bos.toByteArray()
  }
}
