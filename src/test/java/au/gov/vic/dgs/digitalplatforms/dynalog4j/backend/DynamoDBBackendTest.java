package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.ArrayList;
import java.util.List;
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
        List<Map<String, AttributeValue>> items = List.of(
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("com.example.Service").build(),
                "level", AttributeValue.builder().s("DEBUG").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("org.springframework").build(),
                "level", AttributeValue.builder().s("WARN").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("root").build(),
                "level", AttributeValue.builder().s("INFO").build()
            )
        );

        QueryResponse response = QueryResponse.builder()
            .items(items)
            .build();

        when(mockDynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result).containsEntry("com.example.Service", "DEBUG");
        assertThat(result).containsEntry("org.springframework", "WARN");
        assertThat(result).containsEntry("root", "INFO");

        verify(mockDynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFetchDesiredLevelsWithNoItems() throws Exception {
        // Arrange
        QueryResponse response = QueryResponse.builder()
            .items(new ArrayList<>())
            .build();
        when(mockDynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).isEmpty();
        verify(mockDynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFetchDesiredLevelsWithInvalidLogLevels() throws Exception {
        // Arrange
        List<Map<String, AttributeValue>> items = List.of(
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("com.example.Valid").build(),
                "level", AttributeValue.builder().s("DEBUG").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("com.example.Invalid").build(),
                "level", AttributeValue.builder().s("INVALID_LEVEL").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("com.example.Empty").build(),
                "level", AttributeValue.builder().s("").build()
            )
        );

        QueryResponse response = QueryResponse.builder()
            .items(items)
            .build();

        when(mockDynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result).containsEntry("com.example.Valid", "DEBUG");
        assertThat(result).doesNotContainKey("com.example.Invalid");
        assertThat(result).doesNotContainKey("com.example.Empty");

        verify(mockDynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFetchDesiredLevelsWithCaseInsensitiveLogLevels() throws Exception {
        // Arrange
        List<Map<String, AttributeValue>> items = List.of(
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("com.example.Lower").build(),
                "level", AttributeValue.builder().s("debug").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("com.example.Mixed").build(),
                "level", AttributeValue.builder().s("WaRn").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("com.example.Upper").build(),
                "level", AttributeValue.builder().s("ERROR").build()
            )
        );

        QueryResponse response = QueryResponse.builder()
            .items(items)
            .build();

        when(mockDynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).hasSize(3);
        assertThat(result).containsEntry("com.example.Lower", "DEBUG");
        assertThat(result).containsEntry("com.example.Mixed", "WARN");
        assertThat(result).containsEntry("com.example.Upper", "ERROR");

        verify(mockDynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFetchDesiredLevelsWithAllValidLogLevels() throws Exception {
        // Arrange
        List<Map<String, AttributeValue>> items = List.of(
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("trace").build(),
                "level", AttributeValue.builder().s("TRACE").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("debug").build(),
                "level", AttributeValue.builder().s("DEBUG").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("info").build(),
                "level", AttributeValue.builder().s("INFO").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("warn").build(),
                "level", AttributeValue.builder().s("WARN").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("error").build(),
                "level", AttributeValue.builder().s("ERROR").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("fatal").build(),
                "level", AttributeValue.builder().s("FATAL").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("off").build(),
                "level", AttributeValue.builder().s("OFF").build()
            )
        );

        QueryResponse response = QueryResponse.builder()
            .items(items)
            .build();

        when(mockDynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

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

        verify(mockDynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFetchDesiredLevelsThrowsExceptionOnDynamoDbError() {
        // Arrange
        RuntimeException dynamoException = new RuntimeException("Table not found");
        when(mockDynamoDbClient.query(any(QueryRequest.class))).thenThrow(dynamoException);

        // Act & Assert
        assertThatThrownBy(() -> backend.fetchDesiredLevels())
            .isInstanceOf(Exception.class)
            .hasMessageContaining("Failed to query DynamoDB")
            .hasCauseInstanceOf(RuntimeException.class);

        verify(mockDynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFetchDesiredLevelsWithItemsMissingAttributes() throws Exception {
        // Arrange
        List<Map<String, AttributeValue>> items = List.of(
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("valid.logger").build(),
                "level", AttributeValue.builder().s("DEBUG").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                // Missing logger attribute
                "level", AttributeValue.builder().s("INFO").build()
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("missing.level").build()
                // Missing level attribute
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("another.valid").build(),
                "level", AttributeValue.builder().s("WARN").build()
            )
        );

        QueryResponse response = QueryResponse.builder()
            .items(items)
            .build();

        when(mockDynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).containsEntry("valid.logger", "DEBUG");
        assertThat(result).containsEntry("another.valid", "WARN");

        verify(mockDynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFetchDesiredLevelsWithTTLItem() throws Exception {
        // Arrange - Items with TTL attributes should still work
        List<Map<String, AttributeValue>> items = List.of(
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("com.example.Temporary").build(),
                "level", AttributeValue.builder().s("DEBUG").build(),
                "ttl", AttributeValue.builder().n("1672531200").build() // TTL timestamp
            ),
            Map.of(
                "service", AttributeValue.builder().s("test-service").build(),
                "logger", AttributeValue.builder().s("com.example.Permanent").build(),
                "level", AttributeValue.builder().s("INFO").build()
                // No TTL - permanent override
            )
        );

        QueryResponse response = QueryResponse.builder()
            .items(items)
            .build();

        when(mockDynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // Act
        Map<String, String> result = backend.fetchDesiredLevels();

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).containsEntry("com.example.Temporary", "DEBUG");
        assertThat(result).containsEntry("com.example.Permanent", "INFO");

        verify(mockDynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void testFetchDesiredLevelsVerifiesCorrectQueryRequest() throws Exception {
        // Arrange
        QueryResponse response = QueryResponse.builder()
            .items(new ArrayList<>())
            .build();
        when(mockDynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // Act
        backend.fetchDesiredLevels();

        // Assert - Verify the query was called with correct parameters
        verify(mockDynamoDbClient, times(1)).query(any(QueryRequest.class));
    }
}
