package au.gov.vic.dgs.digitalplatforms.dynalog4j.backend;

import java.util.Map;

/**
 * Interface for pluggable backends that provide desired log level overrides.
 */
public interface Backend {
    /**
     * Fetch desired log levels as a map of logger name to level.
     * 
     * @return Map where keys are logger names and values are log levels (e.g., "DEBUG", "INFO", "WARN", "ERROR")
     * @throws Exception if unable to fetch desired levels
     */
    Map<String, String> fetchDesiredLevels() throws Exception;
}
