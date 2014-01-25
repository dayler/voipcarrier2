/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nuevatel.common.helper.xml;

import java.util.List;

/**
 * The selected XML node.
 * 
 * @author asalazar
 */
public interface XmlNode {

    /**
     * @return The text node value.
     */
    String getNodeValue();

    /**
     * @return True if the text node value is blank.
     */
    boolean isEmptyValue();

    /**
     * @return True if the node has more childs.
     */
    boolean hasChilds();

    /**
     * Gets the selected node list to corresponds for the evaluated XPath.
     * 
     * @param xpath The XPath for selecting nodes in the XML document.
     * 
     * @return The selected node list to corresponds for the evaluated XPath.
     */
    List<XmlNode> getNodeList(String xpath);

    /**
     * Gets the first node of the node list for the evaluated XPath.
     * 
     * @param xpath The XPath for selecting nodes in the XML document.
     * 
     * @return The first node of the node list for the evaluated XPath.
     */
    XmlNode getFirstNode(String xpath);

    /**
     * Get a particular attribute value for the node.
     * 
     * @param attributeName The attribute name
     * @return The particular attribute value for the node.
     */
    String getAttribute(String attributeName);

    /**
     * @return The number of the attributes contained in the node.
     */
    int getAttributeCount();
}
