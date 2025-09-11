package au.gov.vic.dgs.digitalplatforms.dynalog4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.gov.vic.dgs.digitalplatforms.dynalog4j.backend.Backend;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.backend.BackendFactory;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.config.AppConfiguration;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.config.ConfigurationReconciler;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.jmx.JMXManager;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.jmx.LoggerContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main application that performs reconciliation loops to manage Log4j2 configuration dynamically.
 */
public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);
    
    private final AppConfiguration config;
    private final Backend backend;
    private final JMXManager jmxManager;
    private final ConfigurationReconciler reconciler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private LoggerContext targetContext;

    public App(AppConfiguration config) {
        this.config = config;
        this.backend = BackendFactory.createBackend(config);
        this.jmxManager = new JMXManager(config);
        this.reconciler = new ConfigurationReconciler();
    }

    public App(AppConfiguration config, Backend backend, JMXManager jmxManager, ConfigurationReconciler reconciler) {
        this.config = config;
        this.backend = backend;
        this.jmxManager = jmxManager;
        this.reconciler = reconciler;
    }

    public static void main(String[] args) {
        // Parse configuration from CLI and environment variables
        AppConfiguration config = AppConfiguration.parse(args);
        
        logger.info("Starting dynalog4j with configuration: {}", config);
        
        App app = new App(config);
        
        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Received shutdown signal, stopping application...");
            app.stop();
        }));
        
        // Run with retry logic if configured
        if (config.getMaxAttempts() > 0) {
            app.runWithRetry();
        } else {
            try {
                app.start();
            } catch (Exception e) {
                logger.error("Application failed to start: {}", e.getMessage(), e);
                System.exit(1);
            }
        }
    }

    /**
     * Start the reconciliation loop.
     */
    public void start() throws Exception {
        if (running.getAndSet(true)) {
            throw new IllegalStateException("Application is already running");
        }

        logger.info("Initializing application with reconcile interval: {}", config.getReconcileInterval());

        // Initial connection and setup
        connectAndDiscover();

        // Main reconciliation loop
        while (running.get()) {
            try {
                performReconciliation();
                Thread.sleep(config.getReconcileInterval().toMillis());
            } catch (InterruptedException e) {
                logger.info("Reconciliation loop interrupted");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error during reconciliation: {}", e.getMessage(), e);
                
                // Try to reconnect on connection errors
                if (!jmxManager.isConnected()) {
                    logger.info("Attempting to reconnect to JMX endpoint...");
                    try {
                        connectAndDiscover();
                    } catch (Exception reconnectError) {
                        logger.error("Reconnection failed: {}", reconnectError.getMessage());
                    }
                }
                
                // Continue the loop even if reconciliation fails
                try {
                    Thread.sleep(Math.min(config.getReconcileInterval().toMillis(), 30000)); // Wait at most 30s on error
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        cleanup();
        logger.info("Application stopped");
    }

    /**
     * Run the application with retry logic for main loop failures.
     */
    public void runWithRetry() {
        int attempt = 0;
        int maxAttempts = config.getMaxAttempts();
        
        while (attempt < maxAttempts && !Thread.currentThread().isInterrupted()) {
            attempt++;
            
            try {
                logger.info("Starting application (attempt {} of {})", attempt, maxAttempts);
                start();
                
                // If we reach here, the application ran successfully and stopped normally
                logger.info("Application completed successfully");
                return;
                
            } catch (Exception e) {
                logger.error("Application failed on attempt {} of {}: {}", attempt, maxAttempts, e.getMessage(), e);
                
                // If this was the last attempt, exit with error
                if (attempt >= maxAttempts) {
                    logger.error("Maximum retry attempts ({}) exceeded. Exiting.", maxAttempts);
                    System.exit(1);
                    return;
                }
                
                // Reset the running flag for next attempt
                running.set(false);
                
                // Wait before retrying
                long retryDelayMs = config.getRetryInterval().toMillis();
                logger.info("Waiting {} seconds before retry attempt {}...", 
                           config.getRetryInterval().getSeconds(), attempt + 1);
                
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    logger.info("Retry delay interrupted");
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Stop the app.
     */
    public void stop() {
        running.set(false);
    }

    private void connectAndDiscover() throws Exception {
        // Connect to JMX
        jmxManager.connect();
        
        // Discover and select LoggerContext
        List<LoggerContext> contexts = jmxManager.discoverLoggerContexts();
        if (contexts.isEmpty()) {
            throw new Exception("No Log4j2 LoggerContexts found in target JVM");
        }
        
        targetContext = jmxManager.selectLoggerContext(contexts);
        logger.info("Target LoggerContext selected: {}", targetContext.getName());
    }

    private void performReconciliation() throws Exception {
        logger.debug("Starting reconciliation cycle...");
        
        // Fetch desired levels from backend
        Map<String, String> desiredLevels = backend.fetchDesiredLevels();
        
        // Get current configuration
        String currentConfig = jmxManager.getConfigurationText(targetContext);
        if (currentConfig == null || currentConfig.trim().isEmpty()) {
            logger.warn("Unable to retrieve current configuration from LoggerContext");
            return;
        }

        // Reconcile configuration (even if desiredLevels is empty, to clean up previous overrides)
        String updatedConfig = reconciler.reconcileConfiguration(currentConfig, desiredLevels);
        
        // Check if configuration actually changed
        if (currentConfig.equals(updatedConfig)) {
            logger.debug("Configuration unchanged, skipping update");
            return;
        }

        // Apply updated configuration (unless in dry-run mode)
        if (config.isDryRun()) {
            if (desiredLevels.isEmpty()) {
                logger.info("DRY RUN: Would clean up any existing log level overrides");
            } else {
                logger.info("DRY RUN: Would update configuration with {} overrides", desiredLevels.size());
            }
            logger.debug("DRY RUN: Updated configuration would be:\n{}", updatedConfig);
        } else {
            jmxManager.setConfigurationText(targetContext, updatedConfig);
            if (desiredLevels.isEmpty()) {
                logger.info("Configuration successfully updated (cleaned up existing overrides)");
            } else {
                logger.info("Configuration successfully updated with {} overrides", desiredLevels.size());
            }
        }
    }

    private void cleanup() {
        if (jmxManager != null) {
            jmxManager.disconnect();
        }
    }
}
