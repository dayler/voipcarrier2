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
import static com.nuevatel.sip.voipcarrier.helper.VoipConstants.*;

/**
 * Event response for WatchEventReportCall.
 *
 * @author asalazar
 */
public class WatchReportRetEventResponse implements EventResponse {

    /**
     * Period for executing watch report call. It is in milliseconds. if it is null indicates that
     * is not possible get more holds for the unit.
     */
    private Integer watchPeriod;

    /**
     * Time to end the call. It is in milliseconds. It is different to null when the user do not
     * have more credit to do holds.
     */
    private Integer watchOffset;

    public WatchReportRetEventResponse(Message message) {
        Parameters.checkNull(message, "message");

        IE rawWatchArg = message.getIE(CFIE.WATCH_ARG_IE);

        if (rawWatchArg == null) {
            throw new IllegalStateException("WATCH_ARG_IE is null.");
        }

        WatchArg watchArg = new WatchArg(rawWatchArg);

        Integer watchArg0 = watchArg.getWatchArg0();
        watchPeriod = watchArg0 == null ? null : watchArg0 * FIX_MILLISECONDS_FACTOR.intValueExact();

        Integer watchArg5 = watchArg.getWatchArg5();
        watchOffset = watchArg5 == null ? null : watchArg5 * FIX_MILLISECONDS_FACTOR.intValueExact();
    }

    /**
     * It is in milliseconds. if it is null indicates that is not possible get more holds for the unit
     *
     * @return Period for executing watch report call.
     */
    public Integer getWatchPeriod() {
        return watchPeriod;
    }

    /**
     * It is in milliseconds. It is different to null when the user do not have more credit to do holds.
     *
     * @return Time to end the call
     */
    public Integer getWatchOffset() {
        return watchOffset;
    }
}
