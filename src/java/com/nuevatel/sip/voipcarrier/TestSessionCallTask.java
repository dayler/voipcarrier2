/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nuevatel.sip.voipcarrier;

import com.nuevatel.base.appconn.AppMessages;
import com.nuevatel.base.appconn.Conn;
import com.nuevatel.base.appconn.Message;
import com.nuevatel.base.appconn.Task;
import com.nuevatel.cf.appconn.CFIE;
import com.nuevatel.cf.appconn.Id;
import com.nuevatel.cf.appconn.TestSessionAsyncRet;
import com.nuevatel.cf.appconn.TestSessionRet;
import java.util.concurrent.TimeUnit;
import org.apache.log4j.Logger;

/**
 *
 * @author Luis Marcelo Baldiviezo <marcelo.baldiviezo@nuevatel.com>
 */
public class TestSessionCallTask implements Task {
    
    private static final Long OFFSET_50MS=50l;
     /**
     * Application logger
     */
    private final static Logger logger = Logger.getLogger(TestSessionCallTask.class);

    public Message execute(Conn conn, Message msg) throws Exception {
        TestSessionRet testSessionRes = null;
        Id tmpId = new Id(msg.getIE(CFIE.ID_IE));
        Call call = CacheHandler.getCacheHandler().getCallsMap().get(tmpId.getId0());
        if (call!=null){
            CacheHandler.getCacheHandler().getTestSessionAsyncRetPool().schedule(new TestSessionAsyncRetWorker(tmpId, call), OFFSET_50MS, TimeUnit.MILLISECONDS);
            testSessionRes = new TestSessionRet(AppMessages.ACCEPTED);
        }
        else testSessionRes = new TestSessionRet(AppMessages.FAILED);
        return testSessionRes.toMessage();
    }
    
    private class TestSessionAsyncRetWorker implements Runnable{
        
        private Id id;
        private Call call;
        
        public TestSessionAsyncRetWorker(Id id, Call call){
            this.id=id;
            this.call=call;
        }

        public void run() {
            try {
                TestSessionAsyncRet testSessionAsynRet = new TestSessionAsyncRet(id, AppMessages.ACCEPTED);
                call.getAppClient().dispatch(testSessionAsynRet.toMessage());
            }catch (Exception ex){
                logger.error("TestSessionCallTask cannot be executed. ",ex);
            }
            
        }
        
    }
}
