/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nuevatel.common.helper.xml;

import com.nuevatel.common.helper.Parameters;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/**
 * XmlHash Implementation for DOM parser.
 *
 * @author asalazar
 */
public class XmlHashImpl implements XmlHash{

    /**
     * Application logger.
     */
    private static final Logger logger = Logger.getLogger(XmlHashImpl.class.getName());

    /**
     * The XML document.
     */
    private Document xmlDocument;

    /**
     * XPath evaluator, used to evaluate XPath expressions.
     */
    private XPath xPath = XPathFactory.newInstance().newXPath();

    /**
     * XmlHashImpl Constructor. Set up the XML Document.
     * 
     * @param xmlDocument The XML Document.
     * 
     * @throws IllegalArgumentException If xmlDocument is null.
     */
    public XmlHashImpl(Document xmlDocument) {
        Parameters.checkNull(xmlDocument, "xmlDocument");
        this.xmlDocument = xmlDocument;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasChilds() {
        return xmlDocument.hasChildNodes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<XmlNode> getNodeList(String xpath) {
        try {
            NodeList nodeList = (NodeList) xPath.compile(xpath).evaluate(xmlDocument,
                    XPathConstants.NODESET);
            return XmlNodeImpl.nodeListToXmlNodes(nodeList);

        } catch (XPathExpressionException ex) {
            logger.log(Level.SEVERE, String.format("Xpath %s cannot be evaluated", xpath), ex);

            // Return null if the xpath cannot be evaluated.
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlNode getFirstNode(String xpath) {
        List<XmlNode> xmlNodes = getNodeList(xpath);
        
        if (xmlNodes == null || xmlNodes.isEmpty()) {
            return null;
        }

        // Get first element.
        return xmlNodes.get(0);
    }

    /**
     * {@inheritDoc}
     */
    public String get(String xpath) {
        try {
            String result = xPath.compile(xpath).evaluate(xmlDocument);

            return result;
        } catch (XPathExpressionException ex) {
            logger.log(Level.SEVERE, String.format("Xpath %s cannot be evaluated", xpath), ex);

            // Return null if the xpath cannot be evaluated.
            return null;
        }
    }
}
