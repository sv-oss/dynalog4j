package au.gov.vic.dgs.digitalplatforms.dynalog4j.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.gov.vic.dgs.digitalplatforms.dynalog4j.config.AppConfiguration;

import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import com.sun.tools.attach.AttachNotSupportedException;
import java.util.Properties;

/**
 * Manages JMX connections to the target JVM for Log4j2 configuration management.
 */
public class JMXManager {
    private static final Logger logger = LoggerFactory.getLogger(JMXManager.class);
    
    private final String jmxUrl;
    private final String jmxPid;
    private final String jmxPidFilter;
    private final String targetLoggerContext;
    private JMXConnector connector;
    private MBeanServerConnection connection;
    private VirtualMachine attachedVM;

    public JMXManager(AppConfiguration config) {
        this.jmxPid = config.getJmxPid();
        this.jmxPidFilter = config.getJmxPidFilter();
        if (jmxPid != null && !jmxPid.trim().isEmpty()) {
            this.jmxUrl = null; // Will be resolved from PID
        } else {
            // Only use URL if explicitly set by user (not defaults)
            boolean hasExplicitHost = !config.getJmxHost().equals("localhost");
            boolean hasExplicitPort = !config.getJmxPort().equals("9999");
            if (hasExplicitHost || hasExplicitPort) {
                this.jmxUrl = config.getJmxUrl();
            } else {
                this.jmxUrl = null; // Let PID discovery work
            }
        }
        this.targetLoggerContext = config.getTargetLoggerContext();
    }

    public JMXManager() {
        this(buildJMXUrl());
    }

    public JMXManager(String jmxUrl) {
        this.jmxUrl = jmxUrl;
        this.jmxPid = null;
        this.jmxPidFilter = null;
        this.targetLoggerContext = System.getenv("TARGET_LOGGER_CONTEXT");
    }

    private static String buildJMXUrl() {
        String host = System.getenv().getOrDefault("JMX_HOST", "localhost");
        String port = System.getenv().getOrDefault("JMX_PORT", "9999");
        return String.format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", host, port);
    }

    /**
     * Connect to the target JVM's JMX endpoint.
     */
    public void connect() throws Exception {
        if (jmxPid != null && !jmxPid.trim().isEmpty()) {
            connectViaPid(jmxPid.trim());
        } else if (jmxUrl != null) {
            connectViaUrl(jmxUrl);
        } else {
            // No PID or URL specified, try to discover available Java processes
            List<String> availablePids = discoverAttachableJavaProcesses(jmxPidFilter);
            if (availablePids.isEmpty()) {
                String filterMsg = (jmxPidFilter != null && !jmxPidFilter.trim().isEmpty()) 
                    ? " matching filter '" + jmxPidFilter + "'" 
                    : "";
                throw new Exception("No JMX PID or URL configured and no attachable Java processes found" + filterMsg);
            } else if (availablePids.size() == 1) {
                logger.info("Found single attachable Java process, using PID: {}", availablePids.get(0));
                connectViaPid(availablePids.get(0));
            } else {
                String filterMsg = (jmxPidFilter != null && !jmxPidFilter.trim().isEmpty()) 
                    ? " matching filter '" + jmxPidFilter + "'" 
                    : "";
                logger.info("Multiple attachable Java processes found{}. Please specify --jmx-pid:", filterMsg);
                for (String pid : availablePids) {
                    logger.info("  - PID: {}", pid);
                }
                throw new Exception("Multiple Java processes available. Please specify --jmx-pid with one of the above PIDs");
            }
        }
    }

    /**
     * Connect to JMX via process ID attachment.
     */
    private void connectViaPid(String pid) throws Exception {
        try {
            logger.info("Attaching to Java process with PID: {}", pid);
            
            // Attach to the target JVM
            attachedVM = VirtualMachine.attach(pid);
            
            // Check if JMX agent is already loaded
            Properties agentProperties = attachedVM.getAgentProperties();
            String jmxAddress = agentProperties.getProperty("com.sun.management.jmxremote.localConnectorAddress");
            
            if (jmxAddress == null) {
                // No JMX agent loaded, start the local management agent
                logger.debug("JMX agent not loaded, starting local management agent");
                
                try {
                    attachedVM.startLocalManagementAgent();
                    
                    // Refresh agent properties to get the connector address
                    agentProperties = attachedVM.getAgentProperties();
                    jmxAddress = agentProperties.getProperty("com.sun.management.jmxremote.localConnectorAddress");
                    
                    if (jmxAddress != null) {
                        logger.debug("Successfully started local management agent");
                    }
                } catch (Exception e) {
                    logger.warn("Failed to start local management agent: {}", e.getMessage());
                    throw new Exception("Failed to start JMX management agent on process " + pid + ": " + e.getMessage(), e);
                }
            }
            
            if (jmxAddress == null) {
                throw new Exception("Failed to obtain JMX connector address from process " + pid);
            }
            
            logger.debug("Found JMX connector address: {}", jmxAddress);
            
            // Connect to the JMX endpoint using the connector address
            connectViaUrl(jmxAddress);
            
            logger.info("Successfully connected to JMX endpoint via PID {}", pid);
            
        } catch (AttachNotSupportedException e) {
            throw new Exception("Cannot attach to process " + pid + ": attach not supported", e);
        } catch (IOException e) {
            throw new Exception("Failed to attach to process " + pid, e);
        }
    }

    /**
     * Connect to JMX via URL.
     */
    private void connectViaUrl(String url) throws Exception {
        try {
            logger.debug("Connecting to JMX endpoint: {}", url);
            JMXServiceURL serviceURL = new JMXServiceURL(url);
            connector = JMXConnectorFactory.connect(serviceURL, null);
            connection = connector.getMBeanServerConnection();
            logger.debug("Successfully connected to JMX endpoint");
        } catch (MalformedURLException e) {
            throw new Exception("Invalid JMX URL: " + url, e);
        } catch (IOException e) {
            throw new Exception("Failed to connect to JMX endpoint: " + url, e);
        }
    }

    /**
     * Disconnect from the JMX endpoint.
     */
    public void disconnect() {
        if (connector != null) {
            try {
                connector.close();
                logger.debug("Disconnected from JMX endpoint");
            } catch (IOException e) {
                logger.warn("Error closing JMX connection: {}", e.getMessage());
            } finally {
                connector = null;
                connection = null;
            }
        }
        
        if (attachedVM != null) {
            try {
                attachedVM.detach();
                logger.debug("Detached from VM");
            } catch (IOException e) {
                logger.warn("Error detaching from VM: {}", e.getMessage());
            } finally {
                attachedVM = null;
            }
        }
    }

    /**
     * Discover all Log4j2 LoggerContext MBeans in the target JVM.
     */
    public List<LoggerContext> discoverLoggerContexts() throws Exception {
        if (connection == null) {
            throw new IllegalStateException("Not connected to JMX endpoint");
        }

        try {
            // Query for all Log4j2 MBeans to see what's available
            ObjectName pattern = new ObjectName("org.apache.logging.log4j2:*");
            Set<ObjectInstance> mbeans = connection.queryMBeans(pattern, null);
            
            logger.debug("Found {} Log4j2 MBeans total:", mbeans.size());
            for (ObjectInstance mbean : mbeans) {
                ObjectName objectName = mbean.getObjectName();
                logger.debug("  - {}", objectName);
            }
            
            // Look for main LoggerContext MBeans with config operations
            List<LoggerContext> contexts = new ArrayList<>();
            
            // Simplified: only look for org.apache.logging.log4j2:type=* MBeans
            ObjectName searchPattern = new ObjectName("org.apache.logging.log4j2:type=*");
            Set<ObjectInstance> foundMbeans = connection.queryMBeans(searchPattern, null);
            
            for (ObjectInstance mbean : foundMbeans) {
                ObjectName objectName = mbean.getObjectName();
                String type = objectName.getKeyProperty("type");
                String component = objectName.getKeyProperty("component");
                
                // Skip component MBeans, we only want main LoggerContext MBeans
                if (component != null) {
                    continue;
                }
                
                logger.debug("Checking LoggerContext MBean: {} (type: '{}')", objectName, type);
                
                // Check if this MBean has the config operations we need
                try {
                    javax.management.MBeanInfo mbeanInfo = connection.getMBeanInfo(objectName);
                    boolean hasGetConfigText = false;
                    boolean hasSetConfigText = false;
                    
                    for (javax.management.MBeanOperationInfo op : mbeanInfo.getOperations()) {
                        String opName = op.getName();
                        if ("getConfigText".equals(opName)) {
                            hasGetConfigText = true;
                            logger.debug("Found getConfigText operation on {}", objectName);
                        } else if ("setConfigText".equals(opName)) {
                            hasSetConfigText = true;
                            logger.debug("Found setConfigText operation on {}", objectName);
                        }
                    }
                    
                    if (hasGetConfigText && hasSetConfigText) {
                        contexts.add(new LoggerContext(objectName, type));
                        logger.debug("Added LoggerContext: {} (type: {})", objectName, type);
                    }
                } catch (Exception e) {
                    logger.debug("Could not inspect MBean {}: {}", objectName, e.getMessage());
                }
            }
            
            // If no specific LoggerContext found, fall back to Loggers MBeans
            if (contexts.isEmpty()) {
                ObjectName loggersPattern = new ObjectName("org.apache.logging.log4j2:type=*,component=Loggers,name=*");
                Set<ObjectInstance> loggersMbeans = connection.queryMBeans(loggersPattern, null);
                for (ObjectInstance mbean : loggersMbeans) {
                    ObjectName objectName = mbean.getObjectName();
                    String contextName = objectName.getKeyProperty("name");
                    contexts.add(new LoggerContext(objectName, contextName));
                }
            }
            
            logger.debug("Discovered {} LoggerContext(s):", contexts.size());
            for (int i = 0; i < contexts.size(); i++) {
                LoggerContext ctx = contexts.get(i);
                String displayName = (ctx.getName() == null || ctx.getName().isEmpty()) 
                    ? "<default-context>" 
                    : ctx.getName();
                logger.debug("  [{}] {} (ObjectName: {})", i + 1, displayName, ctx.getObjectName());
            }
            return contexts;
            
        } catch (Exception e) {
            throw new Exception("Failed to discover LoggerContexts", e);
        }
    }

    /**
     * Select the appropriate LoggerContext based on configuration or auto-detection.
     */
    public LoggerContext selectLoggerContext(List<LoggerContext> contexts) {
        if (contexts.isEmpty()) {
            throw new IllegalArgumentException("No LoggerContexts found");
        }
        
        if (targetLoggerContext != null && !targetLoggerContext.trim().isEmpty()) {
            // User specified a target context pattern (supports regex)
            String pattern = targetLoggerContext.trim();
            
            for (LoggerContext context : contexts) {
                String contextName = context.getName();
                if (contextName != null) {
                    try {
                        // Try regex matching first
                        if (contextName.matches(pattern)) {
                            logger.info("Using LoggerContext matching pattern '{}': {}", pattern, contextName);
                            return context;
                        }
                    } catch (Exception e) {
                        // If regex fails, try exact match
                        if (pattern.equals(contextName)) {
                            logger.info("Using LoggerContext (exact match): {}", contextName);
                            return context;
                        }
                    }
                }
            }
            
            throw new IllegalArgumentException("No LoggerContext found matching pattern '" + pattern + "'. Available contexts: " + 
                contexts.stream().map(LoggerContext::getName).collect(java.util.stream.Collectors.toList()));
        }

        // Auto-detection: prefer non-system contexts, or use the first one
        LoggerContext selected = contexts.stream()
                .filter(ctx -> !isSystemContext(ctx.getName()))
                .findFirst()
                .orElse(contexts.get(0));
        
        String selectedDisplayName = (selected.getName() == null || selected.getName().isEmpty()) 
            ? "<default-context>" 
            : selected.getName();

        logger.debug("Auto-selected LoggerContext: {} (ObjectName: {})", 
                   selectedDisplayName, selected.getObjectName());
        return selected;
    }

    private boolean isSystemContext(String contextName) {
        if (contextName == null) {
            return false;
        }
        String lower = contextName.toLowerCase();
        return lower.contains("system") || 
               lower.contains("bootstrap") || 
               lower.contains("platform") ||
               lower.length() == 36; // UUID-like system-generated names
    }

    /**
     * Get the current Log4j2 configuration as XML text.
     */
    public String getConfigurationText(LoggerContext context) throws Exception {
        if (connection == null) {
            throw new IllegalStateException("Not connected to JMX endpoint");
        }

        try {
            // First, let's try to list available operations for debugging
            javax.management.MBeanInfo mbeanInfo = connection.getMBeanInfo(context.getObjectName());
            logger.debug("Available operations for {}: ", context.getObjectName());
            for (javax.management.MBeanOperationInfo op : mbeanInfo.getOperations()) {
                logger.debug("  - {} returns {} ({})", op.getName(), op.getReturnType(), op.getDescription());
                for (javax.management.MBeanParameterInfo param : op.getSignature()) {
                    logger.debug("    param: {} {}", param.getType(), param.getName());
                }
            }
            
            // Try the most likely Log4j2 JMX methods based on documentation
            String[] possibleMethods = {"getConfigText", "getConfiguration", "getConfigurationText", "getConfig", "getConfigName", "getConfigLocation"};
            
            for (String methodName : possibleMethods) {
                try {
                    Object result = connection.invoke(
                        context.getObjectName(),
                        methodName,
                        new Object[]{"UTF-8"},
                        new String[]{"java.lang.String"}
                    );
                    
                    if (result != null) {
                        logger.debug("Successfully retrieved configuration using method: {}", methodName);
                        return result.toString();
                    }
                } catch (javax.management.ReflectionException e) {
                    // Try without parameters
                    try {
                        Object result = connection.invoke(
                            context.getObjectName(),
                            methodName,
                            new Object[]{},
                            new String[]{}
                        );
                        
                        if (result != null) {
                            logger.debug("Successfully retrieved configuration using method: {} (no params)", methodName);
                            return result.toString();
                        }
                    } catch (Exception ignored) {
                        // Continue to next method
                    }
                }
            }
            
            throw new Exception("No suitable method found to retrieve configuration text");
        } catch (Exception e) {
            throw new Exception("Failed to get configuration text from LoggerContext: " + context.getName(), e);
        }
    }

    /**
     * Set the Log4j2 configuration using XML text.
     */
    public void setConfigurationText(LoggerContext context, String configXml) throws Exception {
        if (connection == null) {
            throw new IllegalStateException("Not connected to JMX endpoint");
        }

        try {
            connection.invoke(
                context.getObjectName(),
                "setConfigText",
                new Object[]{configXml, "UTF-8"},
                new String[]{"java.lang.String", "java.lang.String"}
            );
            
            logger.info("Successfully updated configuration for LoggerContext: {}", context.getName());
        } catch (Exception e) {
            throw new Exception("Failed to set configuration text for LoggerContext: " + context.getName(), e);
        }
    }

    /**
     * Set the configuration location URI (alternative to setConfigurationText).
     */
    public void setConfigurationLocation(LoggerContext context, String configUri) throws Exception {
        if (connection == null) {
            throw new IllegalStateException("Not connected to JMX endpoint");
        }

        try {
            connection.setAttribute(
                context.getObjectName(),
                new javax.management.Attribute("ConfigLocationUri", configUri)
            );
            
            logger.debug("Successfully updated configuration location for LoggerContext: {} to {}", 
                       context.getName(), configUri);
        } catch (Exception e) {
            throw new Exception("Failed to set configuration location for LoggerContext: " + context.getName(), e);
        }
    }

    public boolean isConnected() {
        return connection != null;
    }

    /**
     * Discover attachable Java processes on the system.
     */
    private List<String> discoverAttachableJavaProcesses(String commandFilter) {
        List<String> attachablePids = new ArrayList<>();
        
        try {
            // Get all available VMs
            List<VirtualMachineDescriptor> vms = VirtualMachine.list();
            String currentPid = String.valueOf(ProcessHandle.current().pid());
            
            logger.debug("Discovering attachable Java processes...");
            logger.debug("Current process PID: {} (will be excluded)", currentPid);
            if (commandFilter != null && !commandFilter.trim().isEmpty()) {
                logger.debug("Using command filter: {}", commandFilter);
            }
            
            for (VirtualMachineDescriptor vm : vms) {
                String pid = vm.id();
                
                // Skip our own process
                if (currentPid.equals(pid)) {
                    logger.debug("Skipping self (PID: {})", pid);
                    continue;
                }
                
                // Try to attach to verify it's actually attachable
                try {
                    VirtualMachine testVm = VirtualMachine.attach(pid);
                    
                    // Get some basic properties to verify it's a real Java process
                    Properties systemProps = testVm.getSystemProperties();
                    String javaVersion = systemProps.getProperty("java.version");
                    String mainClass = systemProps.getProperty("sun.java.command", "unknown");
                    
                    testVm.detach();
                    
                    // Apply command filter if specified
                    if (commandFilter != null && !commandFilter.trim().isEmpty()) {
                        boolean matches = false;
                        try {
                            // Try regex matching first
                            matches = mainClass.matches(commandFilter);
                        } catch (Exception e) {
                            // Fall back to simple contains matching
                            matches = mainClass.toLowerCase().contains(commandFilter.toLowerCase());
                        }
                        
                        if (!matches) {
                            logger.debug("PID {} command '{}' does not match filter '{}', skipping", 
                                       pid, mainClass, commandFilter);
                            continue;
                        }
                    }
                    
                    attachablePids.add(pid);
                    logger.debug("Found attachable Java process - PID: {}, Java: {}, Command: {}", 
                               pid, javaVersion, mainClass.length() > 80 ? mainClass.substring(0, 80) + "..." : mainClass);
                    
                } catch (Exception e) {
                    logger.debug("Cannot attach to PID {}: {}", pid, e.getMessage());
                }
            }
            
            if (attachablePids.isEmpty()) {
                String filterMsg = (commandFilter != null && !commandFilter.trim().isEmpty()) 
                    ? " matching filter '" + commandFilter + "'" 
                    : "";
                logger.warn("No attachable Java processes found{}", filterMsg);
            } else {
                String filterMsg = (commandFilter != null && !commandFilter.trim().isEmpty()) 
                    ? " matching filter '" + commandFilter + "'" 
                    : "";
                logger.debug("Found {} attachable Java process(es){}", attachablePids.size(), filterMsg);
            }
            
        } catch (Exception e) {
            logger.warn("Error discovering Java processes: {}", e.getMessage());
        }
        
        return attachablePids;
    }
}
