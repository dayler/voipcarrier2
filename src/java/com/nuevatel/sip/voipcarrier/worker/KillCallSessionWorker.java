/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier.worker;

import com.nuevatel.common.helper.Parameters;
import com.nuevatel.sip.voipcarrier.Call;
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

        call.kill();

        logger.info(String.format("Call: %s was killed.", call.getCallID()));
    }

}
