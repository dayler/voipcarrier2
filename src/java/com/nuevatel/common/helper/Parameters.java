/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nuevatel.common.helper;

/**
 * Helper class to execute validations on parameters value.
 * 
 * @author asalazar
 */
public final class Parameters {

    /**
     * Throws {@link IllegalArgumentException} if "param" is null
     * 
     * @param param The parameter to evaluate.
     * @param paramName The parameter name.
     * 
     * @throws IllegalArgumentException if "param" is null.
     */
    public static void checkNull(Object param, String paramName) {
        if (param == null) {
            throw new IllegalArgumentException(String.format("The parameter: %s is null", paramName));
        }
    }

    /**
     * Throws {@link IllegalArgumentException} if "param" is null, empty string or blank string.
     * 
     * @param param The parameter to evaluate.
     * @param paramName The parameter name.
     * 
     * @throws IllegalArgumentException if "param" is null, empty string or blank string.
     */
    public static void checkBlankString(String param, String paramName) {
        if (StringHelper.isBlank(param)) {
            throw new IllegalArgumentException(String.format("The parameter: %s is empty or null", paramName));
        }
    }
}
