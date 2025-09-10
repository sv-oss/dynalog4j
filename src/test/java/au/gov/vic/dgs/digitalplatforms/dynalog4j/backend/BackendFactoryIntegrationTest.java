package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import org.junit.jupiter.api.Test;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.config.AppConfiguration;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Simple integration tests for BackendFactory that test the default behavior
 * using the new configuration-based approach.
 */
class BackendFactoryIntegrationTest {

    @Test
    void testCreateBackendReturnsValidBackend() throws Exception {
        // Arrange - Create a default configuration
        AppConfiguration config = AppConfiguration.parse(new String[]{});

        // Act - BackendFactory should return a valid backend based on configuration
        Backend backend = BackendFactory.createBackend(config);

        // Assert - Should return some valid backend type
        assertThat(backend).isNotNull();
        assertThat(backend).isInstanceOfAny(EnvBackend.class, FileBackend.class, DynamoDBBackend.class);
    }

    @Test
    void testEnvBackendCreation() throws Exception {
        // Arrange
        AppConfiguration config = AppConfiguration.parse(new String[]{"--backend", "env"});

        // Act
        Backend backend = BackendFactory.createBackend(config);

        // Assert
        assertThat(backend).isNotNull();
        assertThat(backend).isInstanceOf(EnvBackend.class);
    }

    @Test
    void testFileBackendCreation() throws Exception {
        // Arrange
        AppConfiguration config = AppConfiguration.parse(new String[]{"--backend", "file", "--config-path", "/nonexistent/path/file.yaml"});

        // Act
        Backend backend = BackendFactory.createBackend(config);

        // Assert
        assertThat(backend).isNotNull();
        assertThat(backend).isInstanceOf(FileBackend.class);
    }

    @Test
    void testEnvBackendDoesNotThrowException() {
        // Act & Assert - EnvBackend should work without throwing exceptions
        assertThatCode(() -> {
            EnvBackend envBackend = new EnvBackend();
            envBackend.fetchDesiredLevels();
        }).doesNotThrowAnyException();
    }

    @Test
    void testFileBackendWithNonExistentFileReturnsEmptyMap() throws Exception {
        // Arrange
        FileBackend fileBackend = new FileBackend("/nonexistent/path/file.yaml");

        // Act
        var result = fileBackend.fetchDesiredLevels();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void testDynamoDBBackendWithMockedClient() throws Exception {
        // Arrange - Create a mock DynamoDB client wrapper
        DynamoDbClientWrapper mockClient = mock(DynamoDbClientWrapper.class);
        
        // Mock response with sample log levels
        Map<String, AttributeValue> mockLoggers = new HashMap<>();
        mockLoggers.put("root", AttributeValue.builder().s("INFO").build());
        mockLoggers.put("com.example.TestClass", AttributeValue.builder().s("DEBUG").build());
        
        Map<String, AttributeValue> mockItem = new HashMap<>();
        mockItem.put("loggers", AttributeValue.builder().m(mockLoggers).build());
        
        GetItemResponse mockResponse = GetItemResponse.builder()
            .item(mockItem)
            .build();
        
        when(mockClient.getItem(any(GetItemRequest.class))).thenReturn(mockResponse);
        
        // Act - Create DynamoDB backend with mocked client
        DynamoDBBackend backend = new DynamoDBBackend("test-table", "test-service", mockClient);
        Map<String, String> result = backend.fetchDesiredLevels();
        
        // Assert
        assertThat(result).isNotEmpty();
        assertThat(result).containsEntry("root", "INFO");
        assertThat(result).containsEntry("com.example.TestClass", "DEBUG");
        verify(mockClient, times(1)).getItem(any(GetItemRequest.class));
    }

    @Test
    void testDynamoDBBackendWithEmptyResponse() throws Exception {
        // Arrange - Create a mock DynamoDB client wrapper that returns empty response
        DynamoDbClientWrapper mockClient = mock(DynamoDbClientWrapper.class);
        
        GetItemResponse emptyResponse = GetItemResponse.builder()
            .build(); // No item
        
        when(mockClient.getItem(any(GetItemRequest.class))).thenReturn(emptyResponse);
        
        // Act - Create DynamoDB backend with mocked client
        DynamoDBBackend backend = new DynamoDBBackend("test-table", "test-service", mockClient);
        Map<String, String> result = backend.fetchDesiredLevels();
        
        // Assert
        assertThat(result).isEmpty();
        verify(mockClient, times(1)).getItem(any(GetItemRequest.class));
    }
}
