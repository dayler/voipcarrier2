/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 *
 * @author luis
 */
public class CacheHandler {

    /**
     * the CacheHandler Object
     */
    private final static CacheHandler cacheHandler = new CacheHandler();

    private ConcurrentHashMap<String, Call> callsMap = new ConcurrentHashMap<String, Call>();

    private ScheduledThreadPoolExecutor testSessionAsynRetPool = new ScheduledThreadPoolExecutor(2);

    private Map<String, ScheduledFuture<?>> scheduledFutureMap = new ConcurrentHashMap<String, ScheduledFuture<?>>();

    private CacheHandler(){
        // No op.
    }

    /**
     * Return the CacheHandler Object
     * @return CacheHandler
     */

    public static CacheHandler getCacheHandler(){
        return cacheHandler;
    }

    public ConcurrentHashMap<String, Call> getCallsMap(){
        return callsMap;
    }

    public ScheduledThreadPoolExecutor getTestSessionAsyncRetPool(){
        return testSessionAsynRetPool;
    }

    public ScheduledFuture<?> popScheduledFuture(String callId) {


        if (scheduledFutureMap.containsKey(callId)) {
            ScheduledFuture<?> schFuture = scheduledFutureMap.get(callId);
            scheduledFutureMap.remove(callId);
            return schFuture;
        }

        return null;
    }

    public void addScheduledFuture(String callId, ScheduledFuture<?> scheduledFuture) {
        if (scheduledFuture != null) {
            scheduledFutureMap.put(callId, scheduledFuture);
        }
    }
}
