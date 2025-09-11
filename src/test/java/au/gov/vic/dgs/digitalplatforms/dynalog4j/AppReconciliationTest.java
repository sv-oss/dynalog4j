package au.gov.vic.dgs.digitalplatforms.dynalog4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import au.gov.vic.dgs.digitalplatforms.dynalog4j.backend.Backend;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.config.AppConfiguration;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.config.ConfigurationReconciler;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.jmx.JMXManager;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.jmx.LoggerContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Tests for App reconciliation behavior, specifically testing the fix for
 * cleaning up overrides when the backend transitions from having entries to empty.
 */
public class AppReconciliationTest {

    @Mock
    private Backend mockBackend;
    
    @Mock
    private JMXManager mockJmxManager;
    
    @Mock
    private ConfigurationReconciler mockReconciler;
    
    @Mock
    private LoggerContext mockLoggerContext;
    
    private AppConfiguration config;
    private App app;

    private final String currentConfigXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Configuration>
            <Loggers>
                <Root level="INFO">
                    <AppenderRef ref="Console"/>
                </Root>
                <!-- dynalog4j-override -->
                <Logger name="com.example.Service" level="DEBUG"/>
            </Loggers>
        </Configuration>
        """;

    private final String cleanedConfigXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Configuration>
            <Loggers>
                <Root level="INFO">
                    <AppenderRef ref="Console"/>
                </Root>
            </Loggers>
        </Configuration>
        """;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = new AppConfiguration();
        config.setDryRun(false); // Ensure we actually apply changes
        
        app = new App(config, mockBackend, mockJmxManager, mockReconciler);
    }

    @Test
    void testReconciliationIsCalledWhenDesiredLevelsIsEmpty() throws Exception {
        // Arrange
        when(mockJmxManager.isConnected()).thenReturn(false, true);
        doNothing().when(mockJmxManager).connect();
        when(mockJmxManager.discoverLoggerContexts()).thenReturn(List.of(mockLoggerContext));
        when(mockJmxManager.selectLoggerContext(any())).thenReturn(mockLoggerContext);
        when(mockLoggerContext.getName()).thenReturn("TestContext");
        
        // Mock backend returning empty map (no configuration found)
        when(mockBackend.fetchDesiredLevels()).thenReturn(new HashMap<>());
        
        // Mock current config contains previous overrides
        when(mockJmxManager.getConfigurationText(mockLoggerContext)).thenReturn(currentConfigXml);
        
        // Mock reconciler cleaning up the config
        when(mockReconciler.reconcileConfiguration(currentConfigXml, new HashMap<>()))
            .thenReturn(cleanedConfigXml);
        
        // Act - Call the private performReconciliation method via starting the app
        // We'll start and immediately stop to trigger one reconciliation cycle
        Thread appThread = new Thread(() -> {
            try {
                app.start();
            } catch (Exception e) {
                // Expected since we'll interrupt the thread
            }
        });
        
        appThread.start();
        Thread.sleep(100); // Allow time for initialization and first reconciliation
        appThread.interrupt();
        appThread.join(1000);
        
        // Assert
        // Verify that reconcileConfiguration was called even with empty desired levels
        verify(mockReconciler, atLeastOnce()).reconcileConfiguration(eq(currentConfigXml), eq(new HashMap<>()));
        
        // Verify that setConfigurationText was called to apply the cleaned config
        verify(mockJmxManager, atLeastOnce()).setConfigurationText(mockLoggerContext, cleanedConfigXml);
    }

    @Test
    void testReconciliationWithNonEmptyDesiredLevels() throws Exception {
        // Arrange
        when(mockJmxManager.isConnected()).thenReturn(false, true);
        doNothing().when(mockJmxManager).connect();
        when(mockJmxManager.discoverLoggerContexts()).thenReturn(List.of(mockLoggerContext));
        when(mockJmxManager.selectLoggerContext(any())).thenReturn(mockLoggerContext);
        when(mockLoggerContext.getName()).thenReturn("TestContext");
        
        // Mock backend returning some overrides
        Map<String, String> desiredLevels = Map.of("com.example.Service", "DEBUG");
        when(mockBackend.fetchDesiredLevels()).thenReturn(desiredLevels);
        
        String basicConfigXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Configuration>
                <Loggers>
                    <Root level="INFO">
                        <AppenderRef ref="Console"/>
                    </Root>
                </Loggers>
            </Configuration>
            """;
        
        when(mockJmxManager.getConfigurationText(mockLoggerContext)).thenReturn(basicConfigXml);
        
        // Mock reconciler applying the overrides
        when(mockReconciler.reconcileConfiguration(basicConfigXml, desiredLevels))
            .thenReturn(currentConfigXml);
        
        // Act
        Thread appThread = new Thread(() -> {
            try {
                app.start();
            } catch (Exception e) {
                // Expected since we'll interrupt the thread
            }
        });
        
        appThread.start();
        Thread.sleep(100); // Allow time for initialization and first reconciliation
        appThread.interrupt();
        appThread.join(1000);
        
        // Assert
        verify(mockReconciler, atLeastOnce()).reconcileConfiguration(eq(basicConfigXml), eq(desiredLevels));
        verify(mockJmxManager, atLeastOnce()).setConfigurationText(mockLoggerContext, currentConfigXml);
    }
}
