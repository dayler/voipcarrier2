/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nuevatel.common.helper.xml;

import java.util.List;

/**
 * Define the interface to handle XML document like hash, in where the key is XPath expression.
 * 
 * @author asalazar
 */
public interface XmlHash {

    /**
     * @return True is the XML document has childs.
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
     * Gets string value for the evaluate XPath, if it does not match with nothing, returns null.
     * 
     * @param xpath The XPath for selecting nodes in the XML document.
     *
     * @return Value for the evaluate XPath, if it does not match with nothing, returns null.
     */
    String get(String xpath);
}
