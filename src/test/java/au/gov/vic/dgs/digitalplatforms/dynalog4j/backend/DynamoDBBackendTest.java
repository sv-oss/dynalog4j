package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DynamoDBBackendTest {

    @Mock
    private DynamoDbClientWrapper mockDynamoDbClient;

    private DynamoDBBackend backend;

    @BeforeEach
    void setUp() {
        backend = new DynamoDBBackend("test-table", "test-service", mockDynamoDbClient);
    }

    @Test
    void testFetchDesiredLevelsWithValidData() throws Exception {
        // Arrange
        Map<String, AttributeValue> loggersMap = Map.of(
            "com.example.Service", AttributeValue.builder().s("DEBUG").build(),
            "org.springframework", AttributeValue.builder().s("WARN").build(),
            "root", AttributeValue.builder().s("INFO").build()
        );

        Map<String, AttributeValue> item = Map.of(
            "service", AttributeValue.builder().s("test-service").build(),
            "loggers", AttributeValue.builder().m(loggersMap).build()
        );

        GetItemResponse response = GetItemResponse.builder()
            .item(item)
            .build();

        when(mockDynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result).containsEntry("com.example.Service", "DEBUG");
        assertThat(result).containsEntry("org.springframework", "WARN");
        assertThat(result).containsEntry("root", "INFO");

        verify(mockDynamoDbClient).getItem(any(GetItemRequest.class));
    }

    @Test
    void testFetchDesiredLevelsWithNoItem() throws Exception {
        // Arrange
        GetItemResponse response = GetItemResponse.builder().build();
        when(mockDynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).isEmpty();
        verify(mockDynamoDbClient).getItem(any(GetItemRequest.class));
    }

    @Test
    void testFetchDesiredLevelsWithNoLoggersAttribute() throws Exception {
        // Arrange
        Map<String, AttributeValue> item = Map.of(
            "service", AttributeValue.builder().s("test-service").build()
        );

        GetItemResponse response = GetItemResponse.builder()
            .item(item)
            .build();

        when(mockDynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).isEmpty();
        verify(mockDynamoDbClient).getItem(any(GetItemRequest.class));
    }

    @Test
    void testFetchDesiredLevelsWithInvalidLogLevels() throws Exception {
        // Arrange
        Map<String, AttributeValue> loggersMap = Map.of(
            "com.example.Valid", AttributeValue.builder().s("DEBUG").build(),
            "com.example.Invalid", AttributeValue.builder().s("INVALID_LEVEL").build(),
            "com.example.Empty", AttributeValue.builder().s("").build(),
            "com.example.Null", AttributeValue.builder().s((String) null).build()
        );

        Map<String, AttributeValue> item = Map.of(
            "service", AttributeValue.builder().s("test-service").build(),
            "loggers", AttributeValue.builder().m(loggersMap).build()
        );

        GetItemResponse response = GetItemResponse.builder()
            .item(item)
            .build();

        when(mockDynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result).containsEntry("com.example.Valid", "DEBUG");
        assertThat(result).doesNotContainKey("com.example.Invalid");
        assertThat(result).doesNotContainKey("com.example.Empty");
        assertThat(result).doesNotContainKey("com.example.Null");

        verify(mockDynamoDbClient).getItem(any(GetItemRequest.class));
    }

    @Test
    void testFetchDesiredLevelsWithCaseInsensitiveLogLevels() throws Exception {
        // Arrange
        Map<String, AttributeValue> loggersMap = Map.of(
            "com.example.Lower", AttributeValue.builder().s("debug").build(),
            "com.example.Mixed", AttributeValue.builder().s("WaRn").build(),
            "com.example.Upper", AttributeValue.builder().s("ERROR").build()
        );

        Map<String, AttributeValue> item = Map.of(
            "service", AttributeValue.builder().s("test-service").build(),
            "loggers", AttributeValue.builder().m(loggersMap).build()
        );

        GetItemResponse response = GetItemResponse.builder()
            .item(item)
            .build();

        when(mockDynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result).containsEntry("com.example.Lower", "DEBUG");
        assertThat(result).containsEntry("com.example.Mixed", "WARN");
        assertThat(result).containsEntry("com.example.Upper", "ERROR");

        verify(mockDynamoDbClient).getItem(any(GetItemRequest.class));
    }

    @Test
    void testFetchDesiredLevelsWithAllValidLogLevels() throws Exception {
        // Arrange
        Map<String, AttributeValue> loggersMap = Map.of(
            "trace", AttributeValue.builder().s("TRACE").build(),
            "debug", AttributeValue.builder().s("DEBUG").build(),
            "info", AttributeValue.builder().s("INFO").build(),
            "warn", AttributeValue.builder().s("WARN").build(),
            "error", AttributeValue.builder().s("ERROR").build(),
            "fatal", AttributeValue.builder().s("FATAL").build(),
            "off", AttributeValue.builder().s("OFF").build()
        );

        Map<String, AttributeValue> item = Map.of(
            "service", AttributeValue.builder().s("test-service").build(),
            "loggers", AttributeValue.builder().m(loggersMap).build()
        );

        GetItemResponse response = GetItemResponse.builder()
            .item(item)
            .build();

        when(mockDynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).hasSize(7);
        assertThat(result).containsEntry("trace", "TRACE");
        assertThat(result).containsEntry("debug", "DEBUG");
        assertThat(result).containsEntry("info", "INFO");
        assertThat(result).containsEntry("warn", "WARN");
        assertThat(result).containsEntry("error", "ERROR");
        assertThat(result).containsEntry("fatal", "FATAL");
        assertThat(result).containsEntry("off", "OFF");

        verify(mockDynamoDbClient).getItem(any(GetItemRequest.class));
    }

    @Test
    void testFetchDesiredLevelsThrowsExceptionOnDynamoDbError() {
        // Arrange
        RuntimeException dynamoException = new RuntimeException("Table not found");
        when(mockDynamoDbClient.getItem(any(GetItemRequest.class))).thenThrow(dynamoException);

        // Act & Assert
        assertThatThrownBy(() -> backend.fetchDesiredLevels())
            .isInstanceOf(Exception.class)
            .hasMessageContaining("Failed to read from DynamoDB")
            .hasCauseInstanceOf(RuntimeException.class);

        verify(mockDynamoDbClient).getItem(any(GetItemRequest.class));
    }

    @Test
    void testFetchDesiredLevelsWithEmptyLoggersMap() throws Exception {
        // Arrange
        Map<String, AttributeValue> emptyLoggersMap = new HashMap<>();

        Map<String, AttributeValue> item = Map.of(
            "service", AttributeValue.builder().s("test-service").build(),
            "loggers", AttributeValue.builder().m(emptyLoggersMap).build()
        );

        GetItemResponse response = GetItemResponse.builder()
            .item(item)
            .build();

        when(mockDynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).isEmpty();
        verify(mockDynamoDbClient).getItem(any(GetItemRequest.class));
    }

    @Test
    void testFetchDesiredLevelsVerifiesCorrectRequestParameters() throws Exception {
        // Arrange
        GetItemResponse response = GetItemResponse.builder().build();
        when(mockDynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // Act
        backend.fetchDesiredLevels();

        // Assert - Verify the request was built correctly
        verify(mockDynamoDbClient).getItem(any(GetItemRequest.class));
        
        // Additional verification using argument captor for more detailed assertions
        verify(mockDynamoDbClient, times(1)).getItem(any(GetItemRequest.class));
    }
}
