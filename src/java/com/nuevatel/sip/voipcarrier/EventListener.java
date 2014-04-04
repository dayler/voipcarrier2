/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier;

import com.nuevatel.sip.voipcarrier.listener.EventListenerResponseSet;

/**
 *
 * @author luis
 */
public interface EventListener  {

    // public EventListenerResponseSet eventReceived (ConversationEvent event) throws Exception;
    //  void
    public void eventReceived (ConversationEvent event, EventListenerResponseSet responseSet) throws Exception;

    public String getRegex();
}
