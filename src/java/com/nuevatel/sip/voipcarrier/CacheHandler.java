/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier;

import java.util.concurrent.ConcurrentHashMap;
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
    
}
