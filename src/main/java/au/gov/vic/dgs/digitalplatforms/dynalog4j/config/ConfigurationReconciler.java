package au.gov.vic.dgs.digitalplatforms.dynalog4j.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

/**
 * Handles reconciliation of Log4j2 XML configuration with desired log level overrides.
 */
public class ConfigurationReconciler {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationReconciler.class);
    
    private final DocumentBuilderFactory documentBuilderFactory;
    private final TransformerFactory transformerFactory;

    public ConfigurationReconciler() {
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.transformerFactory = TransformerFactory.newInstance();
    }

    /**
     * Reconcile the current Log4j2 configuration with desired log level overrides.
     * 
     * @param currentConfigXml Current Log4j2 configuration as XML
     * @param desiredLevels Map of logger names to desired levels
     * @return Updated XML configuration
     * @throws Exception if reconciliation fails
     */
    public String reconcileConfiguration(String currentConfigXml, Map<String, String> desiredLevels) throws Exception {
        if (currentConfigXml == null || currentConfigXml.trim().isEmpty()) {
            throw new IllegalArgumentException("Current configuration XML cannot be null or empty");
        }

        if (desiredLevels.isEmpty()) {
            logger.debug("No desired levels specified, cleaning up any existing overrides");
            return cleanupDynamicLoggers(currentConfigXml);
        }

        try {
            // Parse the current configuration
            Document document = parseXml(currentConfigXml);
            Element root = document.getDocumentElement();

            // Find or create the Loggers section
            Element loggersElement = findOrCreateLoggersElement(document, root);

            // First, remove any loggers that were previously added by overrides but are no longer desired
            removeDynamicLoggersNotInDesiredState(loggersElement, desiredLevels);

            // Apply desired level overrides
            for (Map.Entry<String, String> entry : desiredLevels.entrySet()) {
                String loggerName = entry.getKey();
                String desiredLevel = entry.getValue();
                
                if ("root".equalsIgnoreCase(loggerName)) {
                    updateRootLogger(loggersElement, desiredLevel);
                } else {
                    updateOrCreateLogger(document, loggersElement, loggerName, desiredLevel);
                }
            }

            // Convert back to XML string
            String updatedXml = documentToString(document);
            logger.info("Successfully reconciled configuration with {} log level overrides", desiredLevels.size());
            return updatedXml;

        } catch (Exception e) {
            logger.error("Failed to reconcile configuration: {}", e.getMessage());
            throw new Exception("Configuration reconciliation failed", e);
        }
    }

    /**
     * Clean up all dynamic loggers when no overrides are desired.
     */
    private String cleanupDynamicLoggers(String currentConfigXml) throws Exception {
        try {
            Document document = parseXml(currentConfigXml);
            Element root = document.getDocumentElement();
            
            NodeList loggersNodes = root.getElementsByTagName("Loggers");
            if (loggersNodes.getLength() > 0) {
                Element loggersElement = (Element) loggersNodes.item(0);
                removeDynamicLoggersNotInDesiredState(loggersElement, Map.of());
            }
            
            return documentToString(document);
        } catch (Exception e) {
            logger.error("Failed to cleanup dynamic loggers: {}", e.getMessage());
            throw new Exception("Dynamic logger cleanup failed", e);
        }
    }

    /**
     * Remove loggers that were previously added by overrides but are no longer in the desired state.
     */
    private void removeDynamicLoggersNotInDesiredState(Element loggersElement, Map<String, String> desiredLevels) {
        NodeList loggerNodes = loggersElement.getElementsByTagName("Logger");
        
        // Create a list of nodes to remove (can't modify NodeList while iterating)
        java.util.List<Element> toRemove = new java.util.ArrayList<>();
        
        for (int i = 0; i < loggerNodes.getLength(); i++) {
            Element loggerElement = (Element) loggerNodes.item(i);
            String loggerName = loggerElement.getAttribute("name");
            boolean isDynamic = isDynamicLogger(loggerElement);
            
            // If this logger was added by our overrides and is no longer desired, mark for removal
            if (isDynamic && !desiredLevels.containsKey(loggerName)) {
                toRemove.add(loggerElement);
                logger.info("Marked dynamic logger '{}' for removal (no longer in desired state)", loggerName);
            }
        }
        
        // Remove the marked loggers (and their associated comments)
        for (Element loggerToRemove : toRemove) {
            String loggerName = loggerToRemove.getAttribute("name");
            
            // Remove the comment marker before the logger if it exists
            Node previousSibling = loggerToRemove.getPreviousSibling();
            if (previousSibling != null && previousSibling.getNodeType() == Node.COMMENT_NODE) {
                String commentText = previousSibling.getNodeValue().trim();
                if (commentText.equals("dynalog4j-override")) {
                    loggersElement.removeChild(previousSibling);
                }
            }
            
            loggersElement.removeChild(loggerToRemove);
            logger.info("Removed dynamic logger '{}'", loggerName);
        }
        
        if (!toRemove.isEmpty()) {
            logger.info("Removed {} dynamic logger(s) that are no longer desired", toRemove.size());
        }
    }

    /**
     * Check if a logger element was dynamically created by checking for a preceding comment marker.
     */
    private boolean isDynamicLogger(Element loggerElement) {
        Node previousSibling = loggerElement.getPreviousSibling();
        
        // Skip whitespace nodes to find the actual previous sibling
        while (previousSibling != null && previousSibling.getNodeType() == Node.TEXT_NODE && 
               previousSibling.getNodeValue().trim().isEmpty()) {
            previousSibling = previousSibling.getPreviousSibling();
        }
        
        if (previousSibling != null && previousSibling.getNodeType() == Node.COMMENT_NODE) {
            String commentText = previousSibling.getNodeValue().trim();
            return "dynalog4j-override".equals(commentText);
        }
        
        return false;
    }

    private Document parseXml(String xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        try (StringReader reader = new StringReader(xml)) {
            return builder.parse(new InputSource(reader));
        }
    }

    private Element findOrCreateLoggersElement(Document document, Element root) {
        // Look for existing Loggers element
        NodeList loggersNodes = root.getElementsByTagName("Loggers");
        if (loggersNodes.getLength() > 0) {
            return (Element) loggersNodes.item(0);
        }

        // Create new Loggers element if it doesn't exist
        Element loggersElement = document.createElement("Loggers");
        root.appendChild(loggersElement);
        logger.debug("Created new Loggers element");
        return loggersElement;
    }

    private void updateRootLogger(Element loggersElement, String desiredLevel) {
        NodeList rootNodes = loggersElement.getElementsByTagName("Root");
        if (rootNodes.getLength() > 0) {
            Element rootElement = (Element) rootNodes.item(0);
            String currentLevel = rootElement.getAttribute("level");
            if (!desiredLevel.equals(currentLevel)) {
                rootElement.setAttribute("level", desiredLevel);
                logger.info("Updated Root logger level from '{}' to '{}'", currentLevel, desiredLevel);
            }
        } else {
            // Create Root logger if it doesn't exist
            Document document = loggersElement.getOwnerDocument();
            Element rootElement = document.createElement("Root");
            rootElement.setAttribute("level", desiredLevel);
            loggersElement.appendChild(rootElement);
            logger.info("Created Root logger with level '{}'", desiredLevel);
        }
    }

    private void updateOrCreateLogger(Document document, Element loggersElement, String loggerName, String desiredLevel) {
        // Look for existing logger with this name
        Element existingLogger = findLoggerByName(loggersElement, loggerName);
        
        if (existingLogger != null) {
            String currentLevel = existingLogger.getAttribute("level");
            if (!desiredLevel.equals(currentLevel)) {
                existingLogger.setAttribute("level", desiredLevel);
                // Mark as dynamically managed if not already marked
                if (!isDynamicLogger(existingLogger)) {
                    markLoggerAsDynamic(document, loggersElement, existingLogger);
                }
                logger.info("Updated logger '{}' level from '{}' to '{}'", loggerName, currentLevel, desiredLevel);
            } else {
                // Level is already correct, but ensure it's marked as dynamically managed
                if (!isDynamicLogger(existingLogger)) {
                    markLoggerAsDynamic(document, loggersElement, existingLogger);
                }
            }
        } else {
            // Create new logger element with comment marker
            createDynamicLogger(document, loggersElement, loggerName, desiredLevel);
            logger.info("Created new logger '{}' with level '{}'", loggerName, desiredLevel);
        }
    }

    /**
     * Mark an existing logger as dynamically managed by adding a comment marker.
     */
    private void markLoggerAsDynamic(Document document, Element loggersElement, Element loggerElement) {
        // Add comment marker before the logger element if not already present
        if (!isDynamicLogger(loggerElement)) {
            org.w3c.dom.Comment comment = document.createComment("dynalog4j-override");
            loggersElement.insertBefore(comment, loggerElement);
        }
    }

    /**
     * Create a new logger element with dynamic marker comment.
     */
    private void createDynamicLogger(Document document, Element loggersElement, String loggerName, String desiredLevel) {
        // Create comment marker
        org.w3c.dom.Comment comment = document.createComment("dynalog4j-override");
        
        // Create new logger element
        Element newLogger = document.createElement("Logger");
        newLogger.setAttribute("name", loggerName);
        newLogger.setAttribute("level", desiredLevel);
        
        // Insert before Root logger if it exists, otherwise append
        Element rootElement = findRootLogger(loggersElement);
        if (rootElement != null) {
            loggersElement.insertBefore(comment, rootElement);
            loggersElement.insertBefore(newLogger, rootElement);
        } else {
            loggersElement.appendChild(comment);
            loggersElement.appendChild(newLogger);
        }
    }

    private Element findLoggerByName(Element loggersElement, String loggerName) {
        NodeList loggerNodes = loggersElement.getElementsByTagName("Logger");
        for (int i = 0; i < loggerNodes.getLength(); i++) {
            Element loggerElement = (Element) loggerNodes.item(i);
            if (loggerName.equals(loggerElement.getAttribute("name"))) {
                return loggerElement;
            }
        }
        return null;
    }

    private Element findRootLogger(Element loggersElement) {
        NodeList rootNodes = loggersElement.getElementsByTagName("Root");
        return rootNodes.getLength() > 0 ? (Element) rootNodes.item(0) : null;
    }

    private String documentToString(Document document) throws TransformerException {
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        
        try (StringWriter writer = new StringWriter()) {
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (IOException e) {
            // StringWriter close() doesn't actually throw IOException, but just in case
            throw new TransformerException("Error closing StringWriter", e);
        }
    }
}
