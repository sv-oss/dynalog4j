package au.gov.vic.dgs.digitalplatforms.dynalog4j.jmx;

import javax.management.ObjectName;

/**
 * Represents a Log4j2 LoggerContext discovered via JMX.
 */
public class LoggerContext {
    private final ObjectName objectName;
    private final String name;

    public LoggerContext(ObjectName objectName, String name) {
        this.objectName = objectName;
        this.name = name;
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "LoggerContext{name='" + name + "', objectName=" + objectName + "}";
    }
}
