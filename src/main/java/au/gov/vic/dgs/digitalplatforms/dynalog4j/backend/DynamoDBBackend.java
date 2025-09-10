package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Backend that reads log level overrides from a DynamoDB table.
 * Expected table structure:
 * - Primary key: service (String) - the service/application name
 * - Attribute: loggers (Map) - map of logger names to levels
 * 
 * Example item:
 * {
 *   "service": "my-app",
 *   "loggers": {
 *     "com.example.Class1": "DEBUG",
 *     "com.example.Class2": "INFO",
 *     "root": "WARN"
 *   }
 * }
 */
public class DynamoDBBackend implements Backend {
    private static final Logger logger = LoggerFactory.getLogger(DynamoDBBackend.class);
    
    private final DynamoDbClientWrapper dynamoDbClient;
    private final String tableName;
    private final String serviceName;

    public DynamoDBBackend() {
        this(
            System.getenv().getOrDefault("DYNAMO_TABLE_NAME", "log-levels"),
            System.getenv().getOrDefault("SERVICE_NAME", "default")
        );
    }

    public DynamoDBBackend(String tableName, String serviceName) {
        this.tableName = tableName;
        this.serviceName = serviceName;
        DynamoDbClient client = DynamoDbClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.dynamoDbClient = new DefaultDynamoDbClientWrapper(client);
    }

    // Constructor for testing
    public DynamoDBBackend(String tableName, String serviceName, DynamoDbClientWrapper dynamoDbClient) {
        this.tableName = tableName;
        this.serviceName = serviceName;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public Map<String, String> fetchDesiredLevels() throws Exception {
        try {
            Map<String, AttributeValue> key = Map.of(
                "service", AttributeValue.builder().s(serviceName).build()
            );

            GetItemRequest request = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(request);
            
            if (!response.hasItem()) {
                logger.info("No configuration found in DynamoDB for service: {}", serviceName);
                return new HashMap<>();
            }

            Map<String, AttributeValue> item = response.item();
            AttributeValue loggersAttribute = item.get("loggers");
            
            if (loggersAttribute == null || !loggersAttribute.hasM()) {
                logger.warn("No 'loggers' map found in DynamoDB item for service: {}", serviceName);
                return new HashMap<>();
            }

            Map<String, String> desiredLevels = new HashMap<>();
            Map<String, AttributeValue> loggersMap = loggersAttribute.m();
            
            for (Map.Entry<String, AttributeValue> entry : loggersMap.entrySet()) {
                String loggerName = entry.getKey();
                String level = entry.getValue().s();
                
                if (isValidLogLevel(level)) {
                    desiredLevels.put(loggerName, level.toUpperCase());
                    logger.debug("Found log level override: {} = {}", loggerName, level);
                } else {
                    logger.warn("Invalid log level '{}' for logger '{}', skipping", level, loggerName);
                }
            }
            
            logger.info("Loaded {} log level overrides from DynamoDB for service {}", 
                       desiredLevels.size(), serviceName);
            return desiredLevels;
            
        } catch (Exception e) {
            logger.error("Error reading from DynamoDB table {}: {}", tableName, e.getMessage());
            throw new Exception("Failed to read from DynamoDB: " + e.getMessage(), e);
        }
    }
    
    private boolean isValidLogLevel(String level) {
        if (level == null || level.trim().isEmpty()) {
            return false;
        }
        
        String upperLevel = level.toUpperCase();
        return upperLevel.equals("TRACE") || 
               upperLevel.equals("DEBUG") || 
               upperLevel.equals("INFO") || 
               upperLevel.equals("WARN") || 
               upperLevel.equals("ERROR") || 
               upperLevel.equals("FATAL") ||
               upperLevel.equals("OFF");
    }
}
