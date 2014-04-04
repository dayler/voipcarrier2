/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nuevatel.sip.voipcarrier;

import com.nuevatel.base.appconn.AppMessages;
import com.nuevatel.base.appconn.Conn;
import com.nuevatel.base.appconn.Message;
import com.nuevatel.base.appconn.Task;
import com.nuevatel.cf.appconn.Action;
import com.nuevatel.cf.appconn.SetSessionCall;
import com.nuevatel.cf.appconn.SetSessionRet;
import org.apache.log4j.Logger;

/**
 *
 * @author Luis Marcelo Baldiviezo <marcelo.baldiviezo@nuevatel.com>
 */
public class SetSessionCallTask implements Task {

    private static Logger logger = Logger.getLogger(SetSessionCallTask.class);

    public Message execute(Conn conn, Message msg) throws Exception {
        logger.debug("Start Event Recieved.");

        SetSessionRet setSessionRet = null;
        SetSessionCall setSessionCall = new SetSessionCall(msg);

        logger.debug("Action: " + setSessionCall.getAction().getSessionAction().name());

        if (setSessionCall.getAction().getSessionAction() == Action.SESSION_ACTION.END) {
            setSessionRet = new SetSessionRet(AppMessages.ACCEPTED);
            Call call = CacheHandler.getCacheHandler().getCallsMap().get(setSessionCall.getId().getId0());

            if (call!=null) {
                call.setStatus(Call.CALL_KILLED);
                logger.debug("Call killed.");
            }
        } else {
            setSessionRet = new SetSessionRet(AppMessages.FAILED);
        }

        logger.debug("End Event Recieved.");

        return setSessionRet.toMessage();
    }
}
