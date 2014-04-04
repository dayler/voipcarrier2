/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier.listener;

import com.nuevatel.common.helper.Parameters;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains a set of EventResponse objects organized by [Listener name][call status] -> Event response
 * Used to retrieve the Event response after listener event execution.
 *
 * @author asalazar
 */
public class EventListenerResponseSet {

    /**
     * Set of Event Responses.
     */
    private Map<String, Map<Integer, EventResponse>> setOfResponses = new HashMap<String, Map<Integer, EventResponse>>();

    /**
     * Register an event response into set.
     *
     * @param converationEventName Event listener name to produce the Event Response.
     * @param callStatus The status of the call from which is produced the Event Response.
     * @param eventResponse The Event Response.
     */
    public void addEventResponse(String converationEventName, Integer callStatus, EventResponse eventResponse) {
        Parameters.checkBlankString(converationEventName, "converationEventName");
        Parameters.checkNull(callStatus, "callStatus");
        Parameters.checkNull(eventResponse, "eventResponse");

        Map<Integer, EventResponse> statusResponseKeyPair;

        if (setOfResponses.containsKey(converationEventName)) {
            // If it already exisit, just pick it.
            statusResponseKeyPair = setOfResponses.get(converationEventName);
        } else {
            // If it does not exisit, create one, and register it.
            statusResponseKeyPair = new HashMap<Integer, EventResponse>();
            setOfResponses.put(converationEventName, statusResponseKeyPair);
        }

        // Add event response.
        statusResponseKeyPair.put(callStatus, eventResponse);
    }

    /**
     *
     * @param converationEventName Name of the event class.
     * @param callStatus Status of the call.
     *
     * @return EventResponse for converationEventName and callStatus. NULL if the pair does not match.
     */
   public EventResponse getEventResponse(String converationEventName, Integer callStatus) {
       if (!setOfResponses.containsKey(converationEventName)) {
           return null;
       }

       Map<Integer, EventResponse> statusResponseKeyPair = setOfResponses.get(converationEventName);

       if (!statusResponseKeyPair.containsKey(callStatus)) {
           return null;
       }

       return statusResponseKeyPair.get(callStatus);
   }
}
