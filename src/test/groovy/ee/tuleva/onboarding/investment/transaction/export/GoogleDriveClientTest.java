package ee.tuleva.onboarding.investment.transaction.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

class GoogleDriveClientTest {

  @Test
  void findFolder_returnsIdWhenFolderExists() {
    var restClient = mock(RestClient.class);
    var spec =
        stubGet(
            restClient,
            Map.of("files", List.of(Map.of("id", "folder-123", "name", "2026_tehingud"))));

    var result = new GoogleDriveClient(restClient).findFolder("parent-id", "2026_tehingud");

    assertThat(result).isEqualTo("folder-123");
    verify(spec).uri(contains("supportsAllDrives=true"), any(Object[].class));
  }

  @Test
  void findFolder_returnsNullWhenFolderDoesNotExist() {
    var restClient = mock(RestClient.class);
    stubGet(restClient, Map.of("files", List.of()));

    var result = new GoogleDriveClient(restClient).findFolder("parent-id", "2026_tehingud");

    assertThat(result).isNull();
  }

  @Test
  void createFolder_returnsNewFolderId() {
    var restClient = mock(RestClient.class);
    var bodyUriSpec = stubPost(restClient, Map.of("id", "new-folder-456"));

    var result = new GoogleDriveClient(restClient).createFolder("parent-id", "02.2026");

    assertThat(result).isEqualTo("new-folder-456");
    verify(bodyUriSpec).uri(contains("supportsAllDrives=true"), any(Object[].class));
  }

  @Test
  void getOrCreateFolder_returnsExistingFolder() {
    var restClient = mock(RestClient.class);
    stubGet(
        restClient, Map.of("files", List.of(Map.of("id", "existing-id", "name", "2026_tehingud"))));

    var result = new GoogleDriveClient(restClient).getOrCreateFolder("parent-id", "2026_tehingud");

    assertThat(result).isEqualTo("existing-id");
  }

  @Test
  void getOrCreateFolder_createsNewFolderWhenNotFound() {
    var restClient = mock(RestClient.class);
    stubGet(restClient, Map.of("files", List.of()));
    stubPost(restClient, Map.of("id", "created-id"));

    var result = new GoogleDriveClient(restClient).getOrCreateFolder("parent-id", "02.2026");

    assertThat(result).isEqualTo("created-id");
  }

  @Test
  void uploadFile_returnsWebViewLink() {
    var restClient = mock(RestClient.class);
    var bodyUriSpec =
        stubPost(
            restClient,
            Map.of(
                "id", "file-789",
                "webViewLink", "https://drive.google.com/file/d/file-789/view"));

    var result =
        new GoogleDriveClient(restClient)
            .uploadFile("folder-id", "SEB_TKF100_indeksfondid_16022026.xlsx", new byte[] {1, 2, 3});

    assertThat(result).isEqualTo("https://drive.google.com/file/d/file-789/view");
    verify(bodyUriSpec)
        .uri(contains("https://www.googleapis.com/upload/drive/v3/files"), any(Object[].class));
  }

  @SuppressWarnings("unchecked")
  private static RestClient.RequestHeadersUriSpec<?> stubGet(
      RestClient restClient, Map<String, Object> response) {
    var spec = mock(RestClient.RequestHeadersUriSpec.class);
    when(restClient.get()).thenReturn(spec);
    when(spec.uri(any(String.class), any(Object[].class))).thenReturn(spec);
    when(spec.retrieve()).thenReturn(mock(RestClient.ResponseSpec.class));

    var responseSpec = spec.retrieve();
    when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);
    return spec;
  }

  @SuppressWarnings("unchecked")
  private static RestClient.RequestBodyUriSpec stubPost(
      RestClient restClient, Map<String, Object> response) {
    var bodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    var responseSpec = mock(RestClient.ResponseSpec.class);

    lenient().when(restClient.post()).thenReturn(bodyUriSpec);
    lenient().when(bodyUriSpec.uri(any(String.class), any(Object[].class))).thenReturn(bodyUriSpec);
    lenient().when(bodyUriSpec.contentType(any())).thenReturn(bodyUriSpec);
    lenient().when(bodyUriSpec.body(any(Object.class))).thenReturn(bodyUriSpec);
    lenient().when(bodyUriSpec.retrieve()).thenReturn(responseSpec);
    lenient().when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(response);
    return bodyUriSpec;
  }
}
