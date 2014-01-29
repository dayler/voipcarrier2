/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip;

/**
 *
 * @author asalazar
 */
public enum SipHeaders {
    SUPPORTED ("Supported"),

    ALLOW("Allow"),

    SESSION_EXPIRES("Session-Expires"),

    MIN_SE("Min-SE"),

    REQUIRE("Require");

    private String header;

    private SipHeaders(String header) {
        this.header =header;
    }

    public String getHeader() {
        return header;
    }

    @Override
    public String toString() {
        return header;
    }


}
