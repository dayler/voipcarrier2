/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier;

/**
 *
 * @author luis
 */
public interface EventListener  {
    public void eventReceived (ConversationEvent event) throws Exception;
    public String getRegex();
}
