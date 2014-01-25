/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nuevatel.common.helper;

/**
 * Contains common operation for handle Integers.
 *
 * @author asalazar
 */
public class IntegerHelper {

    /**
     * Parse a String to its Integer representation. If the operation not succeeded returns null.
     * 
     * @param rawValue The String to parse.
     * 
     * @return The Integer representation of the String. If the operation not succeeded returns null.
     */
    public static Integer tryParse(String rawValue) {
        if (StringHelper.isBlank(rawValue))
        {
            // No Parseable.
            return null;
        }

        try {
            // Try to parse.
            return Integer.parseInt(rawValue);
        }
        catch (NumberFormatException ex) {
            // No Parseable.
            return null;
        }
    }
}
