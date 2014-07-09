/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier.worker;

import com.nuevatel.common.helper.Parameters;
import com.nuevatel.sip.voipcarrier.CacheHandler;
import com.nuevatel.sip.voipcarrier.Call;
import java.util.concurrent.ScheduledFuture;
import javax.servlet.sip.SipServletResponse;
import org.apache.log4j.Logger;

/**
 *
 * @author asalazar
 */
public class KillCallSessionWorker implements Runnable {

    private static Logger logger = Logger.getLogger(KillCallSessionWorker.class);

    private Call call;

    public KillCallSessionWorker(Call call) {
        Parameters.checkNull(call, "call");

        this.call = call;
    }

    public void run() {
        logger.info("Callback is executing.");

        ScheduledFuture<?> schFeture = CacheHandler.getCacheHandler().popScheduledFuture(call.getCallID());

        if (schFeture != null) {
            schFeture.cancel(true);
        }

        call.end(SipServletResponse.SC_REQUEST_TERMINATED);

        logger.info(String.format("Call: %s was killed.", call.getCallID()));
    }

}
