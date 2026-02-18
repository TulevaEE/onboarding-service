package ee.tuleva.onboarding.investment.transaction.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
@RequiredArgsConstructor
class GoogleDriveClient {

  private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;

  String findFolder(String parentId, String name) {
    var query =
        "mimeType='%s' and name='%s' and '%s' in parents and trashed=false"
            .formatted(FOLDER_MIME_TYPE, name, parentId);

    Map<String, Object> response =
        restClient
            .get()
            .uri(
                "/files?q={query}&fields=files(id,name)&supportsAllDrives=true&includeItemsFromAllDrives=true",
                query)
            .retrieve()
            .body(MAP_TYPE);

    @SuppressWarnings("unchecked")
    var files = (List<Map<String, Object>>) response.get("files");
    if (files == null || files.isEmpty()) {
      return null;
    }
    return (String) files.getFirst().get("id");
  }

  String createFolder(String parentId, String name) {
    var metadata = Map.of("name", name, "mimeType", FOLDER_MIME_TYPE, "parents", List.of(parentId));

    Map<String, Object> response =
        restClient
            .post()
            .uri("/files?fields={fields}&supportsAllDrives=true", "id")
            .contentType(MediaType.APPLICATION_JSON)
            .body(metadata)
            .retrieve()
            .body(MAP_TYPE);

    return (String) response.get("id");
  }

  String getOrCreateFolder(String parentId, String name) {
    var existingId = findFolder(parentId, name);
    if (existingId != null) {
      return existingId;
    }
    return createFolder(parentId, name);
  }

  String uploadFile(String folderId, String fileName, byte[] content) {
    var boundary = "tuleva_upload_boundary";
    var metadata = Map.of("name", fileName, "parents", List.of(folderId));

    var metadataJson = toJson(metadata);
    var multipartBody = buildMultipartBody(boundary, metadataJson, content);

    Map<String, Object> response =
        restClient
            .post()
            .uri(
                "/upload/drive/v3/files?uploadType=multipart&fields={fields}&supportsAllDrives=true",
                "id,webViewLink")
            .contentType(MediaType.parseMediaType("multipart/related; boundary=" + boundary))
            .body(multipartBody)
            .retrieve()
            .body(MAP_TYPE);

    return (String) response.get("webViewLink");
  }

  private static byte[] buildMultipartBody(
      String boundary, String metadataJson, byte[] fileContent) {
    var metadataPart =
        ("--%s\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n%s\r\n"
            .formatted(boundary, metadataJson));
    var filePart =
        ("--%s\r\nContent-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n"
            .formatted(boundary));
    var closing = "\r\n--%s--".formatted(boundary);

    var metadataBytes = metadataPart.getBytes();
    var filePartBytes = filePart.getBytes();
    var closingBytes = closing.getBytes();

    var result =
        new byte
            [metadataBytes.length
                + filePartBytes.length
                + fileContent.length
                + closingBytes.length];
    System.arraycopy(metadataBytes, 0, result, 0, metadataBytes.length);
    System.arraycopy(filePartBytes, 0, result, metadataBytes.length, filePartBytes.length);
    System.arraycopy(
        fileContent, 0, result, metadataBytes.length + filePartBytes.length, fileContent.length);
    System.arraycopy(
        closingBytes,
        0,
        result,
        metadataBytes.length + filePartBytes.length + fileContent.length,
        closingBytes.length);

    return result;
  }

  private static String toJson(Map<String, Object> map) {
    try {
      return new ObjectMapper().writeValueAsString(map);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize JSON", e);
    }
  }
}
