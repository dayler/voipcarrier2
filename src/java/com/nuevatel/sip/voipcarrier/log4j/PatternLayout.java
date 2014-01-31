/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier.log4j;

import com.nuevatel.common.helper.StringHelper;
import java.util.Date;

/**
 * Overwrite default PatternLayout in order to insert a header in the created log file.
 *
 * @author asalazar
 */
public class PatternLayout extends org.apache.log4j.PatternLayout {

    /**
     * Version for the application.
     */
    private static String version = StringHelper.EMPTY;

    /**
     * Application name.
     */
    private static String appName = StringHelper.EMPTY;

    /**
     * Set application version  to log in the log file header.
     *
     * @param ver Version to set.
     */
    public static void setVersion(String ver) {
        if (StringHelper.isBlank(version)) {
            version = ver;
        }
    }

    /**
     * Name of the application to log in the log file header.
     *
     * @param name Name of the application.
     */
    public static void setAppName(String name) {
        if (StringHelper.isBlank(appName)) {
            appName = name;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHeader() {
        StringBuilder builder = new StringBuilder("##");
        builder.append(StringHelper.END_LINE);
        builder.append("##");
        builder.append(StringHelper.END_LINE);
        builder.append(StringHelper.END_LINE);

        builder.append("Application: ");
        builder.append(appName);
        builder.append(StringHelper.END_LINE);

        builder.append("Version: ");
        builder.append(version);
        builder.append(StringHelper.END_LINE);

        builder.append("Creation Date: ");
        builder.append(new Date());
        builder.append(StringHelper.END_LINE);
        builder.append(StringHelper.END_LINE);

        builder.append("##");
        builder.append(StringHelper.END_LINE);
        builder.append("##");
        builder.append(StringHelper.END_LINE);
        builder.append(StringHelper.END_LINE);

        return builder.toString();
    }

}
