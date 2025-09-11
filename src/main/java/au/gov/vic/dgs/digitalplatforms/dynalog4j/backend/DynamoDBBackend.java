package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Backend that reads log level overrides from a DynamoDB table.
 * Expected table structure:
 * - Composite primary key: service (String) as partition key, logger (String) as sort key
 * - Attribute: level (String) - the log level for this logger
 * - Optional attribute: ttl (Number) - Unix timestamp for TTL expiration
 * 
 * Example items:
 * {
 *   "service": "my-app",
 *   "logger": "com.example.Class1", 
 *   "level": "DEBUG",
 *   "ttl": 1672531200
 * }
 * {
 *   "service": "my-app",
 *   "logger": "root",
 *   "level": "WARN"
 * }
 */
public class DynamoDBBackend implements Backend {
    private static final Logger logger = LoggerFactory.getLogger(DynamoDBBackend.class);
    
    private final DynamoDbClientWrapper dynamoDbClient;
    private final DynamoDbClient awsClient; // Keep reference to close it
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
        this.awsClient = DynamoDbClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.dynamoDbClient = new DefaultDynamoDbClientWrapper(awsClient);
    }

    // Constructor for testing
    public DynamoDBBackend(String tableName, String serviceName, DynamoDbClientWrapper dynamoDbClient) {
        this.tableName = tableName;
        this.serviceName = serviceName;
        this.dynamoDbClient = dynamoDbClient;
        this.awsClient = null; // No AWS client to close in test mode
    }

    @Override
    public Map<String, String> fetchDesiredLevels() throws Exception {
        try {
            QueryRequest request = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("service = :service")
                    .expressionAttributeValues(Map.of(
                        ":service", AttributeValue.builder().s(serviceName).build()
                    ))
                    .build();

            QueryResponse response = dynamoDbClient.query(request);
            
            if (!response.hasItems() || response.items().isEmpty()) {
                logger.debug("No configuration found in DynamoDB for service: {}", serviceName);
                return new HashMap<>();
            }

            Map<String, String> desiredLevels = new HashMap<>();
            
            for (Map<String, AttributeValue> item : response.items()) {
                AttributeValue loggerAttribute = item.get("logger");
                AttributeValue levelAttribute = item.get("level");
                
                if (loggerAttribute == null || loggerAttribute.s() == null || 
                    levelAttribute == null || levelAttribute.s() == null) {
                    logger.warn("Invalid item structure in DynamoDB - missing logger or level attribute");
                    continue;
                }
                
                String loggerName = loggerAttribute.s();
                String level = levelAttribute.s();
                
                if (isValidLogLevel(level)) {
                    desiredLevels.put(loggerName, level.toUpperCase());
                    logger.debug("Found log level override: {} = {}", loggerName, level);
                } else {
                    logger.warn("Invalid log level '{}' for logger '{}', skipping", level, loggerName);
                }
            }
            
            if (desiredLevels.isEmpty()) {
                logger.debug("No valid log level overrides found in DynamoDB for service {}", serviceName);
            } else {
                logger.info("Loaded {} log level overrides from DynamoDB for service {}", 
                           desiredLevels.size(), serviceName);
            }
            return desiredLevels;
            
        } catch (Exception e) {
            logger.error("Error querying DynamoDB table {}: {}", tableName, e.getMessage());
            throw new Exception("Failed to query DynamoDB: " + e.getMessage(), e);
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
    
    @Override
    public void close() {
        if (awsClient != null) {
            try {
                awsClient.close();
                logger.debug("Closed DynamoDB client");
            } catch (Exception e) {
                logger.warn("Error closing DynamoDB client: {}", e.getMessage());
            }
        }
    }
}
