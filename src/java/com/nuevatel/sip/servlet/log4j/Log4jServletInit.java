/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.servlet.log4j;

import com.nuevatel.common.helper.xml.XmlHash;
import com.nuevatel.sip.voipcarrier.helper.ConfigHelper;
import com.nuevatel.sip.voipcarrier.log4j.PatternLayout;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import org.apache.log4j.extras.DOMConfigurator;
import static com.nuevatel.sip.voipcarrier.helper.VoipConstants.*;

/**
 * This servlet is responsible to load the initial configuration for Log4j.xml.
 * It must to set 'load-on-startup=true'. The relative path is configuring as
 * init parameter. Only use XML configuration files, *.properties is not supported.
 *
 * @author asalazar
 */
public class Log4jServletInit extends HttpServlet {

    /**
     * Application logger.
     */
    private static Logger helperLogger = Logger.getLogger("Log4jServletInit");

    /**
     * Path to get the location of the voipcarrier configuration file.
     */
    private static final String XPATH_VERSION = "//config/@version";

    /**
     * Path to get the location of the voipcarrier configuration file.
     */
    private static final String XPATH_APPNAME = "//config/@application";

    /**
     * Name of the init parameter to contains the name of the configuration
     * file. This file must to be XML.
     */
    public static final String LOG4J_INIT_FILE = "log4j-init-xml-file";

    /**
     * {@inheritDoc}
     */
    @Override
    public void init() {
        try {
            String relativePath = getServletContext().getRealPath(ROOT_PATH);

            // Setup header for log file.
            XmlHash confXmlHash = ConfigHelper.getConfigXmlHash(relativePath);
            PatternLayout.setAppName(confXmlHash.get(XPATH_APPNAME));
            PatternLayout.setVersion(confXmlHash.get(XPATH_VERSION));

            String log4jFile = getInitParameter(LOG4J_INIT_FILE);

            // if the log4j-init-file is not set, then no point in trying
            if (log4jFile != null && !log4jFile.isEmpty()) {
                // Load property configuration.
                DOMConfigurator.configure(String.format("%s%s", relativePath, log4jFile));
            }
        } catch (Throwable ex) {
            helperLogger.log(Level.SEVERE, "Critical LOG4J cannot be initialized.", ex);
            System.out.println("Critical LOG4J cannot be initialized.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() {
        // No op.
        super.destroy();
    }

    /** 
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Initialize LOG4J.XML properties";
    }

}
