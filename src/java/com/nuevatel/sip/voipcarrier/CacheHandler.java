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
    private static CacheHandler cacheHandler;
    private ConcurrentHashMap<String, Call> callsMap = new ConcurrentHashMap<String, Call>();
    private ScheduledThreadPoolExecutor testSessionAsynRetPool = new ScheduledThreadPoolExecutor(2);


    private CacheHandler(){
    }

    /**
     * Return the CacheHandler Object
     * @return CacheHandler
     */

    public static CacheHandler getCacheHandler(){
        if (cacheHandler==null) cacheHandler = new CacheHandler();
        return cacheHandler;
    }

    public ConcurrentHashMap<String, Call> getCallsMap(){
        return callsMap;
    }

    public void setCallsMap(ConcurrentHashMap<String, Call> callsMap){
        this.callsMap=callsMap;
    }
    
    public ScheduledThreadPoolExecutor getTestSessionAsyncRetPool(){
        return testSessionAsynRetPool;
    }
    
}
