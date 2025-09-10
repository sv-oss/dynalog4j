package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Backend that reads log level overrides from environment variables.
 * Environment variables should be in the format: LOG_LEVEL_&lt;logger.name&gt;=&lt;level&gt;
 * For example: LOG_LEVEL_com.myco.Class=DEBUG
 */
public class EnvBackend implements Backend {
    private static final Logger logger = LoggerFactory.getLogger(EnvBackend.class);
    private static final String LOG_LEVEL_PREFIX = "LOG_LEVEL_";

    @Override
    public Map<String, String> fetchDesiredLevels() throws Exception {
        Map<String, String> desiredLevels = new HashMap<>();
        
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String envVar = entry.getKey();
            if (envVar.startsWith(LOG_LEVEL_PREFIX)) {
                String loggerName = envVar.substring(LOG_LEVEL_PREFIX.length());
                String level = entry.getValue();
                
                if (isValidLogLevel(level)) {
                    desiredLevels.put(loggerName, level.toUpperCase());
                    logger.debug("Found log level override: {} = {}", loggerName, level);
                } else {
                    logger.warn("Invalid log level '{}' for logger '{}', skipping", level, loggerName);
                }
            }
        }
        
        logger.info("Loaded {} log level overrides from environment variables", desiredLevels.size());
        return desiredLevels;
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
