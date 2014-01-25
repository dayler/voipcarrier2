/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nuevatel.common.helper;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common utility classes to handle exceptions.
 * 
 * @author asalazar
 */
public class ExceptionHelper {

    /**
     * Application logger.
     */
    private static final Logger logger = Logger.getLogger(ExceptionHelper.class.getName());

    /**
     * Parse the exception to get the VONE response error, if it exist.
     * 
     * @param ex The exception from which parse the error message.
     * 
     * @return The parsed exception message.
     */
    public static String getExceptionMessage(Exception ex) {
        if (ex == null)
        {
            throw new NullPointerException("Exception source is null");
        }

        String rawMessage = ex.getMessage();
        String message;

        if (!StringHelper.isEmptyOrNull(rawMessage)
                && rawMessage.contains("Exception_Exception"))
        {
            // Get real meessage error.
            message = rawMessage.split("Exception_Exception: ")[1].split("Please")[0];
        }
        else
        {
            logger.log(Level.INFO, null, ex);
            message = rawMessage;
        }

        return message;
    }
}
