/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.exception;

/**
 * Used when a value is not the expected.
 *
 * @author asalazar
 */
public class IllegalValueException extends Exception{

    /**
     * Default Constructor.
     */
    public IllegalValueException() {
        super();
    }

    /**
     * Constructor, used to indicate an error message.
     *
     * @param msg Exception message.
     */
    public IllegalValueException(String msg) {
        super(msg);
    }

    /**
     * Constructor used to indicate the exception origin, and a additional message
     *
     * @param msg Exception message
     * @param thrwbl Origin.
     */
    public IllegalValueException(String msg, Throwable thrwbl) {
        super(msg, thrwbl);
    }
}
