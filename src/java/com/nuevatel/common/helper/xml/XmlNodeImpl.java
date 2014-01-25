/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nuevatel.common.helper.xml;

import com.nuevatel.common.helper.Parameters;
import com.nuevatel.common.helper.StringHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * XmlNode implementation for DOM Parser.
 *
 * @author asalazar
 */
public class XmlNodeImpl implements XmlNode{

    /**
     * Application logger.
     */
    private static final Logger logger = Logger.getLogger(XmlHashImpl.class.getName());

    /**
     * XPath evaluator. Used to evaluate XPath expressions.
     */
    private XPath xPath = XPathFactory.newInstance().newXPath();

    /**
     * The DOM Node.
     */
    private Node node;

    /**
     * The text content of the node.
     */
    private String text;

    /**
     * Map of the attributes of the DOM node.
     */
    private NamedNodeMap attributes;

    public XmlNodeImpl(Node node) {
        Parameters.checkNull(node, "node");
        this.node = node;
        text = node.getTextContent();
        attributes = node.getAttributes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getNodeValue() {
        return text;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmptyValue() {
        return StringHelper.isBlank(text);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasChilds() {
        return node.hasChildNodes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<XmlNode> getNodeList(String xpathExpresion) {
        Parameters.checkBlankString(xpathExpresion, "xpathExpresion");

        try {
            NodeList nodeList = (NodeList) xPath.compile(xpathExpresion).evaluate(node,
                    XPathConstants.NODESET);
            return nodeListToXmlNodes(nodeList);

        } catch (XPathExpressionException ex) {
            logger.log(Level.SEVERE, String.format("Xpath %s cannot be evaluated", xpathExpresion), ex);

            // Return null if the xpath cannot be evaluated.
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlNode getFirstNode(String xpathExpresion) {
        Parameters.checkBlankString(xpathExpresion, xpathExpresion);
        List<XmlNode> nodes = getNodeList(xpathExpresion);

        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        return nodes.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAttribute(String propName) {
        Parameters.checkBlankString(propName, "propName");

        if (attributes.getLength() == 0) {
            return null;
        }

        return attributes.getNamedItem(propName).getNodeValue();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getAttributeCount() {
        return attributes.getLength();
    }

    /**
     * Convert NodeList into List
     * 
     * @param nodeList The nodelist to convert
     * 
     * @return The list to contains the all elements of nodelist.
     */
    public static List<XmlNode> nodeListToXmlNodes(NodeList nodeList) {
        List<XmlNode> xmlNodes = new ArrayList<XmlNode>();

        if (nodeList != null && nodeList.getLength() > 0){
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                xmlNodes.add(new XmlNodeImpl(node));
            }
        }

        return xmlNodes;
    }
}
