package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Backend that reads log level overrides from a YAML or JSON file.
 * Expected file format:
 * loggers:
 *   com.example.Class1: DEBUG
 *   com.example.Class2: INFO
 *   root: WARN
 */
public class FileBackend implements Backend {
    private static final Logger logger = LoggerFactory.getLogger(FileBackend.class);
    private final String configPath;
    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public FileBackend() {
        this(System.getenv().getOrDefault("LOG_CONFIG_PATH", "/config/log-levels.yaml"));
    }

    public FileBackend(String configPath) {
        this.configPath = configPath;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.jsonMapper = new ObjectMapper();
    }

    @Override
    public Map<String, String> fetchDesiredLevels() throws Exception {
        Path path = Paths.get(configPath);
        
        if (!Files.exists(path)) {
            logger.warn("Configuration file {} does not exist, returning empty configuration", configPath);
            return new HashMap<>();
        }

        try {
            String content = Files.readString(path);
            ObjectMapper mapper = configPath.toLowerCase().endsWith(".json") ? jsonMapper : yamlMapper;
            
            JsonNode root = mapper.readTree(content);
            JsonNode loggersNode = root.get("loggers");
            
            if (loggersNode == null || !loggersNode.isObject()) {
                logger.warn("Configuration file {} does not contain a 'loggers' object", configPath);
                return new HashMap<>();
            }

            Map<String, String> desiredLevels = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = loggersNode.fields();
            
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String loggerName = field.getKey();
                String level = field.getValue().asText();
                
                if (isValidLogLevel(level)) {
                    desiredLevels.put(loggerName, level.toUpperCase());
                    logger.debug("Found log level override: {} = {}", loggerName, level);
                } else {
                    logger.warn("Invalid log level '{}' for logger '{}', skipping", level, loggerName);
                }
            }
            
            logger.info("Loaded {} log level overrides from file {}", desiredLevels.size(), configPath);
            return desiredLevels;
            
        } catch (IOException e) {
            logger.error("Error reading configuration file {}: {}", configPath, e.getMessage());
            throw new Exception("Failed to read configuration file: " + configPath, e);
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
