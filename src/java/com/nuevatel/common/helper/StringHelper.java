/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nuevatel.common.helper;

/**
 * Contains common operations to handle Strings.
 * 
 * @author asalazar
 */
public class StringHelper {
    
    public static final String END_LINE = System.getProperty("line.separator");

    /**
     * Define empty string.
     */
    public static final String EMPTY = "";

    /**
     * Indicates is the string is empty or null
     * 
     * @param str The string to evaluate.
     * 
     * @return True if the string is empty, false for null.
     */
    public static boolean isEmptyOrNull(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Indicates id the string is empty, null or blank
     * 
     * @param str The string to evaluate
     * 
     * @return true if the string is empty, false for null or whitespace
     */
    public static boolean  isBlank(String str) {
        if (isEmptyOrNull(str))
        {
            return true;
        }

        // Goes through whole string
        for (int i = 0; i < str.length(); i++)
        {
            if (Character.isWhitespace(str.charAt(i)) == false)
            {
                // If at least one of its chars is not whitespace
                return false;
            }
        }

        return true;
    }
}
