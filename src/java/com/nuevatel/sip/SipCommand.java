/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip;

import com.nuevatel.common.helper.StringHelper;

/**
 *
 * @author asalazar
 */
public enum SipCommand {
    ACK("ACK"),

    CANCEL("CANCEL"),

    BYE("BYE"),

    INVITE("INVITE");


    private String cmd;

    private SipCommand(String cmd) {
        this.cmd = cmd;
    }

    public String getCommand() {
        return cmd;
    }

    public boolean isSameCmd(String cmd) {
        if (StringHelper.isEmptyOrNull(cmd)) {
            return false;
        } else {
            return this.cmd.equalsIgnoreCase(cmd);
        }
    }

    @Override
    public String toString() {
        return cmd;
    }
}
