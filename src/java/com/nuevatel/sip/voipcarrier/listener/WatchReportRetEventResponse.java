/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier.listener;

import com.nuevatel.base.appconn.IE;
import com.nuevatel.base.appconn.Message;
import com.nuevatel.cf.appconn.CFIE;
import com.nuevatel.cf.appconn.WatchArg;
import com.nuevatel.common.helper.Parameters;

/**
 *
 * @author asalazar
 */
public class WatchReportRetEventResponse implements EventResponse {

    private static final int MILLISECONDS_FIX_FACTOR = 10;

    private Integer watchPeriod;

    private Integer watchOffset;

    public WatchReportRetEventResponse(Message message) {
        Parameters.checkNull(message, "message");

        IE rawWatchArg = message.getIE(CFIE.WATCH_ARG_IE);

        if (rawWatchArg == null) {
            throw new IllegalStateException("WATCH_ARG_IE is null.");
        }

        WatchArg watchArg = new WatchArg(rawWatchArg);

        Integer watchArg0 = watchArg.getWatchArg0();
        watchPeriod = watchArg0 == null ? null : watchArg0 * MILLISECONDS_FIX_FACTOR;

        Integer watchArg5 = watchArg.getWatchArg5();
        watchOffset = watchArg5 == null ? null : watchArg5 * MILLISECONDS_FIX_FACTOR;
    }

    public Integer getWatchPeriod() {
        return watchPeriod;
    }

    public Integer getWatchOffset() {
        return watchOffset;
    }
}
