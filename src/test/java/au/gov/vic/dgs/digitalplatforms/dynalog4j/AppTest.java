package au.gov.vic.dgs.digitalplatforms.dynalog4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import au.gov.vic.dgs.digitalplatforms.dynalog4j.backend.Backend;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.config.AppConfiguration;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.config.ConfigurationReconciler;
import au.gov.vic.dgs.digitalplatforms.dynalog4j.jmx.JMXManager;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the App class, focusing on retry functionality.
 */
public class AppTest {

    @Mock
    private Backend mockBackend;
    
    @Mock
    private JMXManager mockJmxManager;
    
    @Mock
    private ConfigurationReconciler mockReconciler;
    
    private AppConfiguration config;
    private App app;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        config = new AppConfiguration();
        config.setMaxAttempts(0); // Default: no retry
        config.setRetryIntervalSeconds(1L); // Short interval for testing
        
        app = new App(config, mockBackend, mockJmxManager, mockReconciler);
    }

    @Test
    void testNoRetryWhenMaxAttemptsIsZero() throws Exception {
        // Configure mocks to throw exception
        when(mockJmxManager.isConnected()).thenReturn(false);
        doThrow(new RuntimeException("Connection failed")).when(mockJmxManager).connect();
        
        // This should throw immediately without retry
        assertThrows(Exception.class, () -> app.start());
        
        // Verify connect was called only once
        verify(mockJmxManager, times(1)).connect();
    }

    @Test
    void testRetryConfigurationParsing() {
        AppConfiguration config = new AppConfiguration();
        
        // Test default values
        assertEquals(0, config.getMaxAttempts());
        assertEquals(60, config.getRetryInterval().getSeconds());
        
        // Test setting values
        config.setMaxAttempts(3);
        config.setRetryIntervalSeconds(30L);
        
        assertEquals(3, config.getMaxAttempts());
        assertEquals(30, config.getRetryInterval().getSeconds());
        
        // Test negative values are handled
        config.setMaxAttempts(-1);
        config.setRetryIntervalSeconds(-10L);
        
        assertEquals(0, config.getMaxAttempts()); // Should default to 0
        assertEquals(60, config.getRetryInterval().getSeconds()); // Should default to 60
    }

    @Test
    void testConfigurationToString() {
        AppConfiguration config = new AppConfiguration();
        config.setMaxAttempts(3);
        config.setRetryIntervalSeconds(45L);
        
        String toString = config.toString();
        assertTrue(toString.contains("maxAttempts=3"));
        assertTrue(toString.contains("retryIntervalSeconds=45"));
    }
}
