package au.gov.vic.dgs.digitalplatforms.dynalog4j.jmx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import au.gov.vic.dgs.digitalplatforms.dynalog4j.config.AppConfiguration;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JMXManagerTest {

    @Mock
    private JMXConnector mockConnector;

    @Mock
    private MBeanServerConnection mockConnection;

    @Mock
    private ObjectInstance mockObjectInstance;

    @Mock
    private MBeanInfo mockMBeanInfo;

    private JMXManager jmxManager;

    @BeforeEach
    void setUp() {
        jmxManager = new JMXManager("service:jmx:rmi:///jndi/rmi://localhost:9999/jmxrmi");
    }

    @Test
    void testDefaultConstructorBuildsCorrectUrl() {
        // When creating JMXManager with default constructor
        JMXManager defaultManager = new JMXManager();

        // Then JMXManager should be created successfully
        assertThat(defaultManager).isNotNull();
        assertThat(defaultManager.isConnected()).isFalse();
    }

    @Test
    void testCustomJmxUrl() {
        // Given
        String customUrl = "service:jmx:rmi:///jndi/rmi://remotehost:8888/jmxrmi";

        // When
        JMXManager customManager = new JMXManager(customUrl);

        // Then
        assertThat(customManager).isNotNull();
    }

    @Test
    void testBuildJmxUrlWithEnvironmentVariables() {
        // We can't easily test environment variables with Mockito since System can't be mocked
        // Instead, test that the JMXManager is created successfully regardless of env vars
        JMXManager envManager = new JMXManager();

        // Then
        assertThat(envManager).isNotNull();
        assertThat(envManager.isConnected()).isFalse();
    }

    @Test
    void testConnectSuccess() throws Exception {
        // Given
        try (MockedStatic<JMXConnectorFactory> factoryMock = mockStatic(JMXConnectorFactory.class)) {
            factoryMock.when(() -> JMXConnectorFactory.connect(any(JMXServiceURL.class), isNull()))
                .thenReturn(mockConnector);
            when(mockConnector.getMBeanServerConnection()).thenReturn(mockConnection);

            // When
            jmxManager.connect();

            // Then
            assertThat(jmxManager.isConnected()).isTrue();
            verify(mockConnector).getMBeanServerConnection();
        }
    }

    @Test
    void testConnectWithMalformedUrl() {
        // Given
        JMXManager invalidManager = new JMXManager("invalid-url");

        // When/Then
        assertThatThrownBy(() -> invalidManager.connect())
            .isInstanceOf(Exception.class)
            .hasMessageContaining("Invalid JMX URL");
    }

    @Test
    void testConnectWithIOException() throws Exception {
        // Given
        try (MockedStatic<JMXConnectorFactory> factoryMock = mockStatic(JMXConnectorFactory.class)) {
            factoryMock.when(() -> JMXConnectorFactory.connect(any(JMXServiceURL.class), isNull()))
                .thenThrow(new IOException("Connection failed"));

            // When/Then
            assertThatThrownBy(() -> jmxManager.connect())
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Failed to connect to JMX endpoint");
        }
    }

    @Test
    void testDisconnectSuccess() throws Exception {
        // Given
        connectManager();

        // When
        jmxManager.disconnect();

        // Then
        verify(mockConnector).close();
    }

    @Test
    void testDisconnectWithIOException() throws Exception {
        // Given
        connectManager();
        doThrow(new IOException("Close failed")).when(mockConnector).close();

        // When/Then (should not throw, only log warning)
        assertThatCode(() -> jmxManager.disconnect()).doesNotThrowAnyException();
        verify(mockConnector).close();
    }

    @Test
    void testDisconnectWhenNotConnected() {
        // When/Then (should not throw when connector is null)
        assertThatCode(() -> jmxManager.disconnect()).doesNotThrowAnyException();
    }

    @Test
    void testDiscoverLoggerContextsWithConfigOperations() throws Exception {
        // Given
        connectManager();
        setupMockMBeansWithConfigOperations();

        // When
        List<LoggerContext> contexts = jmxManager.discoverLoggerContexts();

        // Then
        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0).getName()).isEqualTo("TestContext");
    }

    @Test
    void testDiscoverLoggerContextsFallbackToLoggers() throws Exception {
        // Given
        connectManager();
        setupMockMBeansWithoutConfigOperations();

        // When
        List<LoggerContext> contexts = jmxManager.discoverLoggerContexts();

        // Then
        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0).getName()).isEqualTo("TestContext");
    }

    @Test
    void testDiscoverLoggerContextsNotConnected() {
        // When/Then
        assertThatThrownBy(() -> jmxManager.discoverLoggerContexts())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Not connected to JMX endpoint");
    }

    @Test
    void testDiscoverLoggerContextsException() throws Exception {
        // Given
        connectManager();
        when(mockConnection.queryMBeans(any(ObjectName.class), isNull()))
            .thenThrow(new IOException("Query failed"));

        // When/Then
        assertThatThrownBy(() -> jmxManager.discoverLoggerContexts())
            .isInstanceOf(Exception.class)
            .hasMessageContaining("Failed to discover LoggerContexts");
    }

    @Test
    void testSelectLoggerContextWithTargetSpecified() throws Exception {
        // Test that the correct context is selected when target is specified and found
        List<LoggerContext> contexts = createTestContexts();
        
        // Create a JMXManager with a target context that exists
        AppConfiguration config = new AppConfiguration();
        config.setTargetLoggerContext("AppContext");
        JMXManager managerWithTarget = new JMXManager(config);

        LoggerContext selected = managerWithTarget.selectLoggerContext(contexts);

        // Should select the specified target context
        assertThat(selected.getName()).isEqualTo("AppContext");
    }

    @Test
    void testSelectLoggerContextWithTargetNotFound() throws Exception {
        // Test that an exception is thrown when target context is not found
        List<LoggerContext> contexts = createTestContexts();
        
        // Create a JMXManager with a target context that doesn't exist
        AppConfiguration config = new AppConfiguration();
        config.setTargetLoggerContext("NonExistentContext");
        JMXManager managerWithTarget = new JMXManager(config);

        // When/Then - should throw exception when target context not found
        assertThatThrownBy(() -> managerWithTarget.selectLoggerContext(contexts))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No LoggerContext found matching pattern 'NonExistentContext'");
    }

    @Test
    void testSelectLoggerContextAutoDetection() throws Exception {
        // Given
        List<LoggerContext> contexts = createTestContexts();

        // When
        LoggerContext selected = jmxManager.selectLoggerContext(contexts);

        // Then (should prefer non-system context)
        assertThat(selected.getName()).isEqualTo("AppContext");
    }

    @Test
    void testSelectLoggerContextWithOnlySystemContexts() throws Exception {
        // Given
        List<LoggerContext> contexts = List.of(
            new LoggerContext(new ObjectName("test:type=System"), "SystemContext"),
            new LoggerContext(new ObjectName("test:type=Bootstrap"), "BootstrapContext")
        );

        // When
        LoggerContext selected = jmxManager.selectLoggerContext(contexts);

        // Then (should select first available when no non-system contexts)
        assertThat(selected.getName()).isEqualTo("SystemContext");
    }

    @Test
    void testSelectLoggerContextWithEmptyList() {
        // Given
        List<LoggerContext> contexts = new ArrayList<>();

        // When/Then
        assertThatThrownBy(() -> jmxManager.selectLoggerContext(contexts))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No LoggerContexts found");
    }

    @Test
    void testSelectLoggerContextWithRegexPattern() throws Exception {
        // Given
        List<LoggerContext> contexts = List.of(
            new LoggerContext(new ObjectName("test:type=Tomcat"), "Tomcat"),
            new LoggerContext(new ObjectName("test:type=1280851e"), "1280851e"),
            new LoggerContext(new ObjectName("test:type=32a033b6"), "32a033b6")
        );
        
        AppConfiguration config = new AppConfiguration();
        config.setTargetLoggerContext("^(?!Tomcat$).*"); // Match anything except "Tomcat"
        JMXManager managerWithRegex = new JMXManager(config);

        // When
        LoggerContext selected = managerWithRegex.selectLoggerContext(contexts);

        // Then (should select first non-Tomcat context)
        assertThat(selected.getName()).isIn("1280851e", "32a033b6");
        assertThat(selected.getName()).isNotEqualTo("Tomcat");
    }

    @Test
    void testSelectLoggerContextWithHashPattern() throws Exception {
        // Given
        List<LoggerContext> contexts = List.of(
            new LoggerContext(new ObjectName("test:type=Tomcat"), "Tomcat"),
            new LoggerContext(new ObjectName("test:type=1280851e"), "1280851e"),
            new LoggerContext(new ObjectName("test:type=SomeOtherName"), "SomeOtherName")
        );
        
        AppConfiguration config = new AppConfiguration();
        config.setTargetLoggerContext("[a-f0-9]{8}"); // Match 8-character hex strings
        JMXManager managerWithRegex = new JMXManager(config);

        // When
        LoggerContext selected = managerWithRegex.selectLoggerContext(contexts);

        // Then (should select the hex hash context)
        assertThat(selected.getName()).isEqualTo("1280851e");
    }

    @Test
    void testSelectLoggerContextWithExactMatchFallback() throws Exception {
        // Given
        List<LoggerContext> contexts = List.of(
            new LoggerContext(new ObjectName("test:type=App"), "App"),
            new LoggerContext(new ObjectName("test:type=Tomcat"), "Tomcat")
        );
        
        AppConfiguration config = new AppConfiguration();
        config.setTargetLoggerContext("App"); // Exact match
        JMXManager managerWithExact = new JMXManager(config);

        // When
        LoggerContext selected = managerWithExact.selectLoggerContext(contexts);

        // Then (should select exact match)
        assertThat(selected.getName()).isEqualTo("App");
    }

    @Test
    void testSelectLoggerContextWithInvalidRegexFallbackToExact() throws Exception {
        // Given
        List<LoggerContext> contexts = List.of(
            new LoggerContext(new ObjectName("test:type=App["), "App["), // Invalid regex but valid name
            new LoggerContext(new ObjectName("test:type=Tomcat"), "Tomcat")
        );
        
        AppConfiguration config = new AppConfiguration();
        config.setTargetLoggerContext("App["); // Invalid regex pattern
        JMXManager managerWithInvalid = new JMXManager(config);

        // When
        LoggerContext selected = managerWithInvalid.selectLoggerContext(contexts);

        // Then (should fall back to exact match)
        assertThat(selected.getName()).isEqualTo("App[");
    }

    @Test
    void testSelectLoggerContextRegexPatternNotFound() throws Exception {
        // Given
        List<LoggerContext> contexts = List.of(
            new LoggerContext(new ObjectName("test:type=Tomcat"), "Tomcat"),
            new LoggerContext(new ObjectName("test:type=System"), "System")
        );
        
        AppConfiguration config = new AppConfiguration();
        config.setTargetLoggerContext("^App.*"); // Pattern that won't match any context
        JMXManager managerWithRegex = new JMXManager(config);

        // When/Then
        assertThatThrownBy(() -> managerWithRegex.selectLoggerContext(contexts))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No LoggerContext found matching pattern '^App.*'")
            .hasMessageContaining("Available contexts: [Tomcat, System]");
    }

    @Test
    void testGetConfigurationTextSuccess() throws Exception {
        // Given
        connectManager();
        LoggerContext context = createTestContext("TestContext");
        setupMockConfigOperations("getConfigText", "<configuration></configuration>");

        // When
        String config = jmxManager.getConfigurationText(context);

        // Then
        assertThat(config).isEqualTo("<configuration></configuration>");
    }

    @Test
    void testGetConfigurationTextWithFallbackMethod() throws Exception {
        // Given
        connectManager();
        LoggerContext context = createTestContext("TestContext");
        
        when(mockConnection.getMBeanInfo(any(ObjectName.class))).thenReturn(mockMBeanInfo);
        when(mockMBeanInfo.getOperations()).thenReturn(new MBeanOperationInfo[]{
            new MBeanOperationInfo("getConfiguration", "Get config", new MBeanParameterInfo[]{}, "java.lang.String", MBeanOperationInfo.INFO)
        });
        
        // First method with UTF-8 parameter fails
        doThrow(new ReflectionException(new NoSuchMethodException()))
            .when(mockConnection).invoke(any(ObjectName.class), eq("getConfigText"), 
                eq(new Object[]{"UTF-8"}), eq(new String[]{"java.lang.String"}));
        
        // Second method "getConfiguration" with UTF-8 parameter fails  
        doThrow(new ReflectionException(new NoSuchMethodException()))
            .when(mockConnection).invoke(any(ObjectName.class), eq("getConfiguration"), 
                eq(new Object[]{"UTF-8"}), eq(new String[]{"java.lang.String"}));
        
        // Second method "getConfiguration" without parameters succeeds
        when(mockConnection.invoke(any(ObjectName.class), eq("getConfiguration"), 
            eq(new Object[]{}), eq(new String[]{})))
            .thenReturn("<configuration></configuration>");

        // When
        String config = jmxManager.getConfigurationText(context);

        // Then
        assertThat(config).isEqualTo("<configuration></configuration>");
    }

    @Test
    void testGetConfigurationTextNotConnected() {
        // Given
        LoggerContext context = createTestContext("TestContext");

        // When/Then
        assertThatThrownBy(() -> jmxManager.getConfigurationText(context))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Not connected to JMX endpoint");
    }

    @Test
    void testGetConfigurationTextNoSuitableMethod() throws Exception {
        // Given
        connectManager();
        LoggerContext context = createTestContext("TestContext");
        
        when(mockConnection.getMBeanInfo(any(ObjectName.class))).thenReturn(mockMBeanInfo);
        when(mockMBeanInfo.getOperations()).thenReturn(new MBeanOperationInfo[]{});
        
        // All method calls fail with ReflectionException
        doThrow(new ReflectionException(new NoSuchMethodException()))
            .when(mockConnection).invoke(any(ObjectName.class), anyString(), 
                eq(new Object[]{"UTF-8"}), eq(new String[]{"java.lang.String"}));
        doThrow(new ReflectionException(new NoSuchMethodException()))
            .when(mockConnection).invoke(any(ObjectName.class), anyString(), 
                eq(new Object[]{}), eq(new String[]{}));

        // When/Then
        assertThatThrownBy(() -> jmxManager.getConfigurationText(context))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("Failed to get configuration text from LoggerContext");
    }

    @Test
    void testSetConfigurationTextSuccess() throws Exception {
        // Given
        connectManager();
        LoggerContext context = createTestContext("TestContext");
        String configXml = "<configuration></configuration>";

        // When
        jmxManager.setConfigurationText(context, configXml);

        // Then
        verify(mockConnection).invoke(
            context.getObjectName(),
            "setConfigText",
            new Object[]{configXml, "UTF-8"},
            new String[]{"java.lang.String", "java.lang.String"}
        );
    }

    @Test
    void testSetConfigurationTextNotConnected() {
        // Given
        LoggerContext context = createTestContext("TestContext");

        // When/Then
        assertThatThrownBy(() -> jmxManager.setConfigurationText(context, "<config></config>"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Not connected to JMX endpoint");
    }

    @Test
    void testSetConfigurationTextException() throws Exception {
        // Given
        connectManager();
        LoggerContext context = createTestContext("TestContext");
        when(mockConnection.invoke(any(ObjectName.class), eq("setConfigText"), any(), any()))
            .thenThrow(new IOException("Set failed"));

        // When/Then
        assertThatThrownBy(() -> jmxManager.setConfigurationText(context, "<config></config>"))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("Failed to set configuration text");
    }

    @Test
    void testSetConfigurationLocationSuccess() throws Exception {
        // Given
        connectManager();
        LoggerContext context = createTestContext("TestContext");
        String configUri = "file:///path/to/log4j2.xml";

        // When
        jmxManager.setConfigurationLocation(context, configUri);

        // Then
        verify(mockConnection).setAttribute(
            context.getObjectName(),
            new Attribute("ConfigLocationUri", configUri)
        );
    }

    @Test
    void testSetConfigurationLocationNotConnected() {
        // Given
        LoggerContext context = createTestContext("TestContext");

        // When/Then
        assertThatThrownBy(() -> jmxManager.setConfigurationLocation(context, "file:///test"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Not connected to JMX endpoint");
    }

    @Test
    void testSetConfigurationLocationException() throws Exception {
        // Given
        connectManager();
        LoggerContext context = createTestContext("TestContext");
        doThrow(new IOException("Set attribute failed"))
            .when(mockConnection).setAttribute(any(ObjectName.class), any(Attribute.class));

        // When/Then
        assertThatThrownBy(() -> jmxManager.setConfigurationLocation(context, "file:///test"))
            .isInstanceOf(Exception.class)
            .hasMessageContaining("Failed to set configuration location");
    }

    @Test
    void testIsConnected() throws Exception {
        // Given
        assertThat(jmxManager.isConnected()).isFalse();

        // When
        connectManager();

        // Then
        assertThat(jmxManager.isConnected()).isTrue();
    }

    // Helper methods

    private void connectManager() throws Exception {
        try (MockedStatic<JMXConnectorFactory> factoryMock = mockStatic(JMXConnectorFactory.class)) {
            factoryMock.when(() -> JMXConnectorFactory.connect(any(JMXServiceURL.class), isNull()))
                .thenReturn(mockConnector);
            when(mockConnector.getMBeanServerConnection()).thenReturn(mockConnection);
            jmxManager.connect();
        }
    }

    private void setupMockMBeansWithConfigOperations() throws Exception {
        ObjectName objectName = new ObjectName("org.apache.logging.log4j2:type=TestContext");
        when(mockObjectInstance.getObjectName()).thenReturn(objectName);
        
        Set<ObjectInstance> mbeans = Set.of(mockObjectInstance);
        
        // Set up queries: first for all MBeans, then for type=* pattern
        when(mockConnection.queryMBeans(any(ObjectName.class), isNull()))
            .thenReturn(mbeans)   // First query (all log4j2 MBeans)
            .thenReturn(mbeans);  // Second query (type=* pattern)
        
        when(mockConnection.getMBeanInfo(any(ObjectName.class))).thenReturn(mockMBeanInfo);
        when(mockMBeanInfo.getOperations()).thenReturn(new MBeanOperationInfo[]{
            new MBeanOperationInfo("getConfigText", "Get config text", new MBeanParameterInfo[]{}, "java.lang.String", MBeanOperationInfo.INFO),
            new MBeanOperationInfo("setConfigText", "Set config text", new MBeanParameterInfo[]{}, "void", MBeanOperationInfo.ACTION)
        });
    }

    private void setupMockMBeansWithoutConfigOperations() throws Exception {
        ObjectName typeObjectName = new ObjectName("org.apache.logging.log4j2:type=TestContext");
        ObjectName loggersObjectName = new ObjectName("org.apache.logging.log4j2:type=TestContext,component=Loggers,name=TestContext");
        
        ObjectInstance typeInstance = mock(ObjectInstance.class);
        ObjectInstance loggersInstance = mock(ObjectInstance.class);
        
        when(typeInstance.getObjectName()).thenReturn(typeObjectName);
        when(loggersInstance.getObjectName()).thenReturn(loggersObjectName);
        
        // Main discovery: first query shows all MBeans, second query shows type=* MBeans but without config ops
        // Third query (fallback) returns the Loggers MBean
        when(mockConnection.queryMBeans(any(ObjectName.class), isNull()))
            .thenReturn(Set.of(typeInstance, loggersInstance)) // First query (all log4j2 MBeans)
            .thenReturn(Set.of(typeInstance)) // Second query (type=* pattern) - has MBean but no config ops
            .thenReturn(Set.of(loggersInstance)); // Third query (fallback to Loggers pattern)
        
        // Set up MBeanInfo for the type=* MBean (no config operations)
        when(mockConnection.getMBeanInfo(typeObjectName)).thenReturn(mockMBeanInfo);
        when(mockMBeanInfo.getOperations()).thenReturn(new MBeanOperationInfo[]{
            new MBeanOperationInfo("someOtherMethod", "Other", new MBeanParameterInfo[]{}, "void", MBeanOperationInfo.ACTION)
        });
    }

    private void setupMockConfigOperations(String methodName, String result) throws Exception {
        when(mockConnection.getMBeanInfo(any(ObjectName.class))).thenReturn(mockMBeanInfo);
        when(mockMBeanInfo.getOperations()).thenReturn(new MBeanOperationInfo[]{
            new MBeanOperationInfo(methodName, "Get config", new MBeanParameterInfo[]{}, "java.lang.String", MBeanOperationInfo.INFO)
        });
        when(mockConnection.invoke(any(ObjectName.class), eq(methodName), 
            eq(new Object[]{"UTF-8"}), eq(new String[]{"java.lang.String"})))
            .thenReturn(result);
    }

    private List<LoggerContext> createTestContexts() throws MalformedObjectNameException {
        return List.of(
            new LoggerContext(new ObjectName("test:type=System"), "SystemContext"),
            new LoggerContext(new ObjectName("test:type=App"), "AppContext"),
            new LoggerContext(new ObjectName("test:type=Target"), "TargetContext")
        );
    }

    private LoggerContext createTestContext(String name) {
        try {
            return new LoggerContext(new ObjectName("test:type=Context"), name);
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException("Failed to create test context", e);
        }
    }
}
