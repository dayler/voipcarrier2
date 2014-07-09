/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nuevatel.sip.voipcarrier.worker;

import java.util.concurrent.ScheduledExecutorService;
import java.util.Date;
import com.nuevatel.base.appconn.AppClient;
import com.nuevatel.sip.voipcarrier.listener.WatchReportRetEventResponse;
import com.nuevatel.cf.appconn.CFIE;
import com.nuevatel.cf.appconn.Action;
import com.nuevatel.base.appconn.Message;
import com.nuevatel.cf.appconn.WatchReportCall;
import com.nuevatel.cf.appconn.WatchArg;
import java.math.RoundingMode;
import com.nuevatel.cf.appconn.Id;
import com.nuevatel.sip.voipcarrier.Call;
import java.math.BigDecimal;
import org.apache.log4j.Logger;
import com.nuevatel.cf.appconn.CFIE.WATCH_TYPE;
import com.nuevatel.cf.appconn.Type;
import com.nuevatel.common.helper.Parameters;
import java.util.concurrent.TimeUnit;
import static com.nuevatel.sip.voipcarrier.helper.VoipConstants.*;

/**
 *
 * @author asalazar
 */
public class DoWatchReportSessionWorker implements Runnable {
    //Callable<Integer> {

    /**
     * Application logger.
     */
    private static Logger logger = Logger.getLogger(DoWatchReportSessionWorker.class);

    private ScheduledExecutorService killSessionExecutorService;

    private Call call;

    private AppClient appClient;

    public DoWatchReportSessionWorker(Call call, AppClient appClient,
            ScheduledExecutorService killSessionExecutorService) {
        Parameters.checkNull(call, "call");
        Parameters.checkNull(appClient, "appClient");
        Parameters.checkNull(killSessionExecutorService, "killSessionExecutorService");

        this.call = call;
        this.appClient = appClient;
        this.killSessionExecutorService = killSessionExecutorService;
    }

    public void run() {
        doWatchReportCall(getCallTimeSpan());
    }

    private synchronized Call getCall() {
        return call;
    }

    /**
     * 
     * @param timeSpan
     * @return
     */
    private void doWatchReportCall(BigDecimal timeSpan) {
        logger.info("Execute doWatchReportCall");

        Id id = new Id(getCall().getCallID(), null);
        long watchArg1 = 0l;
        int timeSpanIntValue =
                timeSpan.setScale(0).divide(FIX_MILLISECONDS_FACTOR, RoundingMode.HALF_UP).intValueExact();
        WatchArg watchArg = new WatchArg(timeSpanIntValue, watchArg1, null, null, null, null);
        WATCH_TYPE typeTimeWatch = Type.WATCH_TYPE.A_TIME_WATCH;
        WatchReportCall resportCall =
                new WatchReportCall(id, typeTimeWatch.getType(), null, watchArg);

        try {
            // Notify to cf.
            Message watchReportRet = appClient.dispatch(resportCall.toMessage());
            Action action = new Action(watchReportRet.getIE(CFIE.ACTION_IE));
            logger.info(String.format(String.format("Session Action: %s", action.getSessionAction().name())));

            if (Action.SESSION_ACTION.END == action.getSessionAction()) {
                // If action is end. Kill the call.
                logger.info(String.format("Call: %s is schduled to kill", getCall().getCallID()));
                killSessionExecutorService.schedule(new KillCallSessionWorker(call),
                        Integer.valueOf(0), TimeUnit.MILLISECONDS);
            } else {

                // Look into watch report rep.
                WatchReportRetEventResponse watchEventResponse = new WatchReportRetEventResponse(watchReportRet);

                logger.info(String.format(
                        "watchReportCall: callId:%s watchType:%s watchTimeValue:%d watchArg1:%d result:%s",
                        getCall().getCallID(), typeTimeWatch.name(), timeSpanIntValue, watchArg1,
                        action.getSessionAction().name()));

                if (watchEventResponse.getWatchOffset() != null) {
                    killSessionExecutorService.schedule(new KillCallSessionWorker(call),
                        watchEventResponse.getWatchOffset(),
                        TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception ex) {
            logger.error("Occurred while WatchReportCall is executing", ex);

            getCall().setStatus(Call.CALL_KILLED);
        }
    }

    /**
     *
     * @param call Current media call in progress.
     *
     * @return How long the call lasted, at the time in which this method is called.
     */
    private BigDecimal getCallTimeSpan() {
        Date referenceEndDate = new Date();

        if (getCall() == null) {
            return BigDecimal.ZERO;
        }

        long referenceEndTime;

        if (!Call.CALL_ENDED.equals(getCall().getStatus()) || getCall().getEndDate() == null) {
            // If the call still in progress.
            referenceEndTime = referenceEndDate.getTime();
        } else {
            referenceEndTime = getCall().getEndDate().getTime();
        }

        BigDecimal creationTime = new BigDecimal(getCall().getStartDate().getTime());
        BigDecimal endTime = new BigDecimal(referenceEndTime);
        BigDecimal timeSpan = creationTime.subtract(endTime).abs();

        return timeSpan;
    }
}
