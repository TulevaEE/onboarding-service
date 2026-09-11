package ee.tuleva.onboarding;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import tools.jackson.databind.json.JsonMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CohesionMetricsTest {

  private static final Path BASELINE_FILE = Path.of("metrics/baseline.json");
  private static final Path OUTPUT_FILE = Path.of("build/metrics/cohesion.json");
  private static final Path CLASSES_DIR = Path.of("build/classes/java/main");
  private static final int DISCONNECTED_THRESHOLD = 2;

  private final JsonMapper mapper = JsonMapper.builder().build();
  private final Map<String, Integer> lcom4ByClass = computeLcom4ByClass();

  @Test
  void emitsCohesionMetrics() throws IOException {
    List<Map<String, Object>> worst =
        lcom4ByClass.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(15)
            .map(
                entry -> Map.<String, Object>of("class", entry.getKey(), "lcom4", entry.getValue()))
            .toList();

    Map<String, Object> metrics = new TreeMap<>();
    metrics.put("disconnectedClasses", disconnectedClassCount());
    metrics.put("worst", worst);

    Files.createDirectories(OUTPUT_FILE.getParent());
    Files.writeString(
        OUTPUT_FILE, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metrics));
  }

  @Test
  void disconnectedClassesDoNotExceedRatchet() throws IOException {
    Map<?, ?> baseline = mapper.readValue(Files.readString(BASELINE_FILE), Map.class);
    if (!baseline.containsKey("disconnectedClasses")) {
      return;
    }
    int allowedDisconnected = ((Number) baseline.get("disconnectedClasses")).intValue();
    assertThat(disconnectedClassCount())
        .as(
            "Disconnected classes (LCOM4>=%s) must never exceed the committed ratchet:"
                + " baseline=%s",
            DISCONNECTED_THRESHOLD, allowedDisconnected)
        .isLessThanOrEqualTo(allowedDisconnected);
  }

  private long disconnectedClassCount() {
    return lcom4ByClass.values().stream().filter(value -> value >= DISCONNECTED_THRESHOLD).count();
  }

  private static Map<String, Integer> computeLcom4ByClass() {
    Map<String, Integer> result = new TreeMap<>();
    try (Stream<Path> files = Files.walk(CLASSES_DIR)) {
      for (Path path : files.filter(path -> path.toString().endsWith(".class")).toList()) {
        ClassNode classNode = readClass(path);
        String className = classNode.name.replace('/', '.');
        if (isAnalyzable(classNode) && !isGeneratedCode(className)) {
          result.put(className, lcom4(classNode));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return result;
  }

  private static boolean isAnalyzable(ClassNode classNode) {
    return (classNode.access & Opcodes.ACC_INTERFACE) == 0;
  }

  private static boolean isGeneratedCode(String className) {
    return className.startsWith("ee.tuleva.onboarding.ariregister.generated.")
        || className.startsWith("ee.tuleva.onboarding.banking.iso20022.");
  }

  private static ClassNode readClass(Path path) {
    try (InputStream in = Files.newInputStream(path)) {
      return readClass(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ClassNode readClassOf(Class<?> type) {
    String resource = "/" + type.getName().replace('.', '/') + ".class";
    try (InputStream in = CohesionMetricsTest.class.getResourceAsStream(resource)) {
      return readClass(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static ClassNode readClass(InputStream in) throws IOException {
    ClassReader reader = new ClassReader(in);
    ClassNode classNode = new ClassNode();
    reader.accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return classNode;
  }

  private static int lcom4(ClassNode classNode) {
    List<MethodNode> eligibleMethods =
        classNode.methods.stream().filter(method -> isEligibleMethod(classNode, method)).toList();
    if (eligibleMethods.size() <= 1) {
      return 1;
    }

    Map<String, String> parent = new HashMap<>();
    for (MethodNode method : eligibleMethods) {
      parent.put(methodId(method), methodId(method));
    }
    for (FieldNode field : classNode.fields) {
      if (isEligibleField(field)) {
        parent.put(fieldId(field), fieldId(field));
      }
    }

    for (MethodNode method : eligibleMethods) {
      String from = methodId(method);
      for (AbstractInsnNode insn : method.instructions) {
        if (insn instanceof FieldInsnNode fieldInsn
            && touchesOwnField(classNode, insn, fieldInsn)) {
          union(parent, from, fieldId(fieldInsn));
        } else if (insn instanceof MethodInsnNode methodInsn
            && callsOwnMethod(classNode, insn, methodInsn)) {
          union(parent, from, methodId(methodInsn));
        }
      }
    }

    Set<String> componentRoots =
        eligibleMethods.stream().map(method -> find(parent, methodId(method))).collect(toSet());
    return componentRoots.size();
  }

  private static boolean touchesOwnField(
      ClassNode classNode, AbstractInsnNode insn, FieldInsnNode fieldInsn) {
    return (insn.getOpcode() == Opcodes.GETFIELD || insn.getOpcode() == Opcodes.PUTFIELD)
        && fieldInsn.owner.equals(classNode.name);
  }

  private static boolean callsOwnMethod(
      ClassNode classNode, AbstractInsnNode insn, MethodInsnNode methodInsn) {
    return (insn.getOpcode() == Opcodes.INVOKEVIRTUAL
            || insn.getOpcode() == Opcodes.INVOKESPECIAL
            || insn.getOpcode() == Opcodes.INVOKEINTERFACE)
        && methodInsn.owner.equals(classNode.name);
  }

  private static void union(Map<String, String> parent, String a, String b) {
    if (!parent.containsKey(a) || !parent.containsKey(b)) {
      return;
    }
    String rootA = find(parent, a);
    String rootB = find(parent, b);
    if (!rootA.equals(rootB)) {
      parent.put(rootA, rootB);
    }
  }

  private static String find(Map<String, String> parent, String id) {
    String root = id;
    while (!parent.get(root).equals(root)) {
      root = parent.get(root);
    }
    parent.put(id, root);
    return root;
  }

  private static String methodId(MethodNode method) {
    return "M#" + method.name + method.desc;
  }

  private static String fieldId(FieldNode field) {
    return "F#" + field.name;
  }

  private static String fieldId(FieldInsnNode fieldInsn) {
    return "F#" + fieldInsn.name;
  }

  private static String methodId(MethodInsnNode methodInsn) {
    return "M#" + methodInsn.name + methodInsn.desc;
  }

  private static boolean isEligibleMethod(ClassNode classNode, MethodNode method) {
    if ((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) {
      return false;
    }
    if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
      return false;
    }
    if (isBoilerplateMethod(method)) {
      return false;
    }
    return !isRecordAccessor(classNode, method);
  }

  private static boolean isBoilerplateMethod(MethodNode method) {
    return switch (method.name) {
      case "equals" -> method.desc.equals("(Ljava/lang/Object;)Z");
      case "hashCode" -> method.desc.equals("()I");
      case "toString" -> method.desc.equals("()Ljava/lang/String;");
      case "canEqual" -> method.desc.equals("(Ljava/lang/Object;)Z");
      case "toBuilder" -> method.desc.startsWith("()");
      default -> false;
    };
  }

  private static boolean isRecordAccessor(ClassNode classNode, MethodNode method) {
    if (!"java/lang/Record".equals(classNode.superName) || classNode.recordComponents == null) {
      return false;
    }
    return classNode.recordComponents.stream()
        .anyMatch(component -> component.name.equals(method.name) && method.desc.startsWith("()"));
  }

  private static boolean isEligibleField(FieldNode field) {
    return (field.access & Opcodes.ACC_STATIC) == 0;
  }

  static class CohesiveFixture {
    private int value;

    void increment() {
      value++;
    }

    int get() {
      return value;
    }
  }

  static class DisjointFixture {
    private int a;
    private int b;

    void bumpA() {
      a++;
    }

    void bumpB() {
      b++;
    }
  }

  static class ChainFixture {
    private int value;

    void a() {
      b();
    }

    void b() {
      value++;
    }
  }

  static class ConstructorOnlyFixture {
    private final int a;
    private final int b;

    ConstructorOnlyFixture(int a, int b) {
      this.a = a;
      this.b = b;
    }

    int getA() {
      return a;
    }

    int getB() {
      return b;
    }
  }

  static class ToStringFixture {
    private int a;
    private int b;

    int getA() {
      return a;
    }

    int getB() {
      return b;
    }

    @Override
    public String toString() {
      return a + "," + b;
    }
  }

  static class FieldOnlyFixture {
    private int a;
    private int b;
  }

  record IsolatedAccessorFixture(int x, int y) {
    int doubledX() {
      return x * 2;
    }

    int identity() {
      return 42;
    }
  }

  @Test
  void scoresCohesiveClassAsOne() {
    assertThat(lcom4(readClassOf(CohesiveFixture.class))).isEqualTo(1);
  }

  @Test
  void scoresDisjointClustersAsTwo() {
    assertThat(lcom4(readClassOf(DisjointFixture.class))).isEqualTo(2);
  }

  @Test
  void scoresMethodChainAsOne() {
    assertThat(lcom4(readClassOf(ChainFixture.class))).isEqualTo(1);
  }

  @Test
  void excludesConstructorsFromConnectingFields() {
    assertThat(lcom4(readClassOf(ConstructorOnlyFixture.class))).isEqualTo(2);
  }

  @Test
  void excludesToStringFromConnectingFields() {
    assertThat(lcom4(readClassOf(ToStringFixture.class))).isEqualTo(2);
  }

  @Test
  void scoresClassWithNoMethodsAsOne() {
    assertThat(lcom4(readClassOf(FieldOnlyFixture.class))).isEqualTo(1);
  }

  @Test
  void excludesRecordAccessorsFromConnectingFields() {
    assertThat(lcom4(readClassOf(IsolatedAccessorFixture.class))).isEqualTo(2);
  }
}
