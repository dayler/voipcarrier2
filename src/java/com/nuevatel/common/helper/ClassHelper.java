/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nuevatel.common.helper;

/**
 *
 * @author asalazar
 */
public class ClassHelper {

    public static <T> T castAs(Class<T> clazz, Object obj) {
        if (obj == null) {
            return null;
        }

        try {
            // Try cast.
            if (clazz.isInstance(obj)) {
                return clazz.cast(obj);
            }

        } catch (ClassCastException ex) {
            // No op.
        }

        return null;
    }
}
