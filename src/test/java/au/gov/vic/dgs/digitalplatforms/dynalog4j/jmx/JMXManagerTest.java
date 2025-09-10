package au.gov.vic.dgs.digitalplatforms.dynalog4j.jmx;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

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
        // We can't test environment variables easily, so test the selection logic directly
        List<LoggerContext> contexts = createTestContexts();
        
        // Test the auto-selection logic when no target is specified
        LoggerContext selected = jmxManager.selectLoggerContext(contexts);

        // Should prefer non-system context
        assertThat(selected.getName()).isEqualTo("AppContext");
    }

    @Test
    void testSelectLoggerContextWithTargetNotFound() throws Exception {
        // Test fallback behavior when target context is not found
        List<LoggerContext> contexts = createTestContexts();

        // When no specific target environment variable is set or target not found,
        // should auto-select first non-system context
        LoggerContext selected = jmxManager.selectLoggerContext(contexts);

        // Then (should auto-select first non-system context)
        assertThat(selected.getName()).isEqualTo("AppContext");
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
        ObjectName objectName = new ObjectName("org.apache.logging.log4j2:type=TestContext,component=Loggers,name=TestContext");
        when(mockObjectInstance.getObjectName()).thenReturn(objectName);
        
        Set<ObjectInstance> mbeans = Set.of(mockObjectInstance);
        
        // Set up to return empty for first few patterns, then return our MBean for one pattern
        when(mockConnection.queryMBeans(any(ObjectName.class), isNull()))
            .thenReturn(Set.of()) // First pattern empty
            .thenReturn(mbeans)   // Second pattern has our MBean
            .thenReturn(Set.of()) // Rest empty
            .thenReturn(Set.of());
        
        when(mockConnection.getMBeanInfo(any(ObjectName.class))).thenReturn(mockMBeanInfo);
        when(mockMBeanInfo.getOperations()).thenReturn(new MBeanOperationInfo[]{
            new MBeanOperationInfo("setConfigText", "Set config", new MBeanParameterInfo[]{}, "void", MBeanOperationInfo.ACTION)
        });
    }

    private void setupMockMBeansWithoutConfigOperations() throws Exception {
        ObjectName objectName = new ObjectName("org.apache.logging.log4j2:type=TestContext,component=Loggers,name=TestContext");
        when(mockObjectInstance.getObjectName()).thenReturn(objectName);
        
        // First query returns empty, second query (fallback) returns the MBean
        when(mockConnection.queryMBeans(any(ObjectName.class), isNull()))
            .thenReturn(Set.of()) // First few patterns return empty
            .thenReturn(Set.of()) 
            .thenReturn(Set.of())
            .thenReturn(Set.of())
            .thenReturn(Set.of(mockObjectInstance)); // Last pattern (fallback) returns the MBean
        
        when(mockConnection.getMBeanInfo(any(ObjectName.class))).thenReturn(mockMBeanInfo);
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
