/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier.helper;

import com.nuevatel.common.helper.xml.XmlHash;
import com.nuevatel.common.helper.xml.XmlHashImpl;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import static com.nuevatel.sip.voipcarrier.helper.VoipConstants.*;

/**
 * Get config xml file common operations.
 *
 * @author asalazar
 */
public class ConfigHelper {

    /**
     *
     * @return XML HASH with config xml configuration.
     *
     * @param relativePath Relative root path, used to locate the confix xml file.
     *
     * @throws SAXException
     * @throws ParserConfigurationException
     * @throws IOException
     */
    public static  XmlHash getConfigXmlHash(String relativePath) throws SAXException,
            ParserConfigurationException, IOException {
        // Load config.xml. Get global configuration.
        String configFile = CONFIG_XML;
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        Document configDoc = builder.parse(String.format("%s%s", relativePath, configFile));
        XmlHash confXmlHash = new XmlHashImpl(configDoc);

        return confXmlHash;
    }
}
