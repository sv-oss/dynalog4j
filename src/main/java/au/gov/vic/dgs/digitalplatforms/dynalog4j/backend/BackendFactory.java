package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.gov.vic.dgs.digitalplatforms.dynalog4j.config.AppConfiguration;

/**
 * Factory for creating backend instances based on configuration.
 */
public class BackendFactory {
    private static final Logger logger = LoggerFactory.getLogger(BackendFactory.class);
    
    public static Backend createBackend(AppConfiguration config) {
        String backendType = config.getBackend();
        
        return switch (backendType) {
            case "env" -> {
                logger.info("Using environment variables backend");
                yield new EnvBackend();
            }
            case "file" -> {
                logger.info("Using file backend with path: {}", config.getConfigPath());
                yield new FileBackend(config.getConfigPath());
            }
            case "dynamodb", "dynamo" -> {
                logger.info("Using DynamoDB backend with table: {}, service: {}", 
                           config.getDynamoTableName(), config.getServiceName());
                yield new DynamoDBBackend(config.getDynamoTableName(), config.getServiceName());
            }
            default -> {
                logger.warn("Unknown backend type '{}', defaulting to environment variables", backendType);
                yield new EnvBackend();
            }
        };
    }
}
