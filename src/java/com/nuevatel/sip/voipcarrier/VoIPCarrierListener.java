/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier;

import com.nuevatel.base.appconn.AppClient;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;
import com.nuevatel.base.appconn.Message;
import com.nuevatel.cf.appconn.Action;
import com.nuevatel.cf.appconn.CFIE;
import com.nuevatel.cf.appconn.EventArg;
import com.nuevatel.cf.appconn.EventReportCall;
import com.nuevatel.cf.appconn.Id;
import com.nuevatel.cf.appconn.Location;
import com.nuevatel.cf.appconn.Name;
import com.nuevatel.cf.appconn.NewSessionCall;
import com.nuevatel.cf.appconn.SessionArg;
import com.nuevatel.cf.appconn.Type;
import com.nuevatel.cf.appconn.WatchArg;
import com.nuevatel.cf.appconn.WatchReportCall;
import java.util.Date;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;


/**
 *
 * @author luis
 */
public class VoIPCarrierListener implements EventListener{
    
    private final static LoggerHandler loggerHandler=new LoggerHandler();
    private final static Logger logger=Logger.getLogger("com.nuevatel.sip.voipcarrier");
    static {
        for(Handler handler : logger.getHandlers()) logger.removeHandler(handler);
        logger.addHandler(loggerHandler);
        logger.setLevel(Level.INFO);
        logger.setUseParentHandlers(false);
    }

    private static class LoggerHandler extends Handler {
        @Override public void publish(LogRecord record) {
            StringBuilder logRecord = new StringBuilder();
            logRecord.append(String.format("%1$tF %1$tT", new Date(record.getMillis()))).append(" ");
            logRecord.append(record.getLevel().getName()).append(" ");
            logRecord.append(record.getSourceClassName()).append(" ");
            logRecord.append(record.getSourceMethodName()).append(" ");
            logRecord.append(record.getMessage());
            System.out.println(logRecord.toString());
        }
        @Override public void flush() {}

        @Override public void close() throws SecurityException {}
    }

    private String cellGlobalId;
    private Integer carrierPrefixSize;
    private String hubbingPrefix;

    public VoIPCarrierListener(String cellGlobalId, Integer carrierPrefixSize, String hubbingPrefix){
        this.cellGlobalId = cellGlobalId;
        this.carrierPrefixSize = carrierPrefixSize;
        this.hubbingPrefix=hubbingPrefix;
    }

    //private variables

    public void eventReceived(ConversationEvent event) throws Exception {
        Call call = (Call)event.getSource();
        AppClient appClient = call.getAppClient();
        
        if (call.getStatus()==Call.CALL_INITIALIZED){
            logger.info("CALL INITIALIZED. callID:"+call.getCallID()+" caller:"+call.getCaller()+" callee:"+call.getCallee()+" status:"+call.getStatus());

            /*The fromName, ie. 70700001 from 70700001@domain1.com:51056*/
            String fromNameString = ((SipURI) call.getCaller()).getUser();
            if (fromNameString.contains("anonymous")) fromNameString = call.getAnonymousMask();

            /*The toName, ie. 70700002 from  sip:267659170700002@domain2.com:5060*/
            String toNameString = ((SipURI)call.getCallee()).getUser().substring(carrierPrefixSize);

            /*The name, ie. 2676 from  sip:267659170700002@domain2.com:5060*/
            String nameString = ((SipURI)call.getCallee()).getUser().substring(0, carrierPrefixSize);
//
//            String nodeId = call.getRURI().toString();
            String nodeId = "sip://"+call.getRemoteHost()+":"+call.getRemotePort();

            //newSessionRequest
            Id id = new Id(call.getCallID(), null);
            Type type = new Type(Type.SERVICE_TYPE.SPEECH, Type.REQUEST_TYPE.O);
            Name name = new Name(nameString, Name.NAI_NATIONAL);
            Name fromName = new Name(fromNameString, Name.NAI_INTERNATIONAL);
            if (!toNameString.startsWith("591")) toNameString = hubbingPrefix+toNameString ; //remove hubbing prefix
            Name toName = new Name(toNameString, Name.TON_INTERNATIONAL);
            Location location = new Location(cellGlobalId,nodeId);
            String tmpReference;
            String tmpPChargingVector = call.getInitialRequest().getHeader("P-Charging-Vector");
            if (tmpPChargingVector!=null){
                String[] tmpArray = tmpPChargingVector.split("=");
                if (tmpArray.length>1) tmpReference = tmpArray[1];
                else tmpReference=tmpPChargingVector;
            }
            else tmpReference = call.getInitialRequest().getCallId();
//            SessionArg sessionArg = new SessionArg(fromName, toName, null, null, tmpReference);
            SessionArg sessionArg = new SessionArg(fromName, toName, null, null, null, tmpReference);
            
            try {
                NewSessionCall newSessionCall = new NewSessionCall(id, type, null, name, location, sessionArg);
                Message newSessionRet = appClient.dispatch(newSessionCall.toMessage());
                Action action = new Action(newSessionRet.getIE(CFIE.ACTION_IE));
                if (action.getSessionAction()==Action.SESSION_ACTION.ACCEPT){
                    /*Modify the callee, ie. sip:59170700002@domain1.com:51056 from  sip:267659170700002@domain2.com:5060*/
                    String newCallee=null;
                    newCallee = "sip:"+((SipURI)call.getCallee()).getUser().substring(4)+"@"+((SipURI)call.getCaller()).getHost()+":"+((SipURI)call.getCaller()).getPort();
                    if (((SipURI)call.getCaller()).getHost().contains("anonymous") || ((SipURI)call.getCaller()).getHost()==null){
                        newCallee = "sip:"+((SipURI)call.getCallee()).getUser().substring(4)+"@"+call.getRemoteHost()+":"+call.getRemotePort();
                    }
                    if (((SipURI)call.getRURI()).getUserParam()!=null) newCallee+=";user="+((SipURI)call.getRURI()).getUserParam();
                    if (((SipURI)call.getRURI()).getTransportParam()!=null) newCallee+=";transport="+((SipURI)call.getRURI()).getTransportParam();
                    call.setCallee(newCallee);
                }
                else{
                    call.setEndType(SipServletResponse.SC_FORBIDDEN);
                    call.end(SipServletResponse.SC_FORBIDDEN);
                }
                logger.info("newSessionCall:"+" name:"+nameString+" fromName:"+fromNameString+" toName:"+toNameString+" callId:"+call.getCallID()+ " cellGlobalId:"+cellGlobalId+" nodeId:"+nodeId+" result:"+action.getSessionAction().name());
                logger.info(name.toString());
                logger.info(fromName.toString());
                logger.info(toName.toString());
            }catch(Exception ex){
                logger.info("newSessionCall Exception:"+" name:"+nameString+" toName:"+toNameString+" callId:"+call.getCallID()+" "+ex.getMessage());
                call.setEndType(SipServletResponse.SC_FORBIDDEN);
                call.end(SipServletResponse.SC_FORBIDDEN);
            }
        }

        else if (call.getStatus()==Call.CALL_STARTED){
            logger.info("CALL STARTED. callID:"+call.getCallID()+" status:"+call.getStatus());

            Id id = new Id(call.getCallID(), null);
            Type type = new Type(Type.SERVICE_TYPE.SPEECH, Type.REQUEST_TYPE.O);
            EventReportCall eventReportCall = new EventReportCall(id, type, Type.EVENT_TYPE.O_ANSWER_2.getType(), null);
            try {
                Message eventReportRet = appClient.dispatch(eventReportCall.toMessage());
                Action action = new Action(eventReportRet.getIE(CFIE.ACTION_IE));
                if (action.getSessionAction() == Action.SESSION_ACTION.ACCEPT){
                    //log something
                }
                else {
                    call.setEndType(SipServletResponse.SC_FORBIDDEN);
                    call.end(SipServletResponse.SC_FORBIDDEN);
                }
                logger.info("eventReportCall:"+" callId:"+call.getCallID()+ " eventType:"+Type.EVENT_TYPE.O_ANSWER_2+" result:"+action.getSessionAction().name());
            }catch (Exception ex){
                logger.warning("eventReportCall Exception:"+" callId:"+call.getCallID()+ " eventType:"+Type.EVENT_TYPE.O_ANSWER_2+" "+ex.getMessage());
                call.setEndType(SipServletResponse.SC_FORBIDDEN);
                call.end(SipServletResponse.SC_FORBIDDEN);
            }
        }

        else if (call.getStatus()==Call.CALL_ENDED){
            if (call.getPastStatus()!=Call.CALL_KILLED){
                logger.info("CALL ENDED. callID:"+call.getCallID()+" status:"+call.getStatus());
                Byte eventType = (((SipURI)call.getEndRequestParty()).toString().equals(((SipURI)call.getCaller()).toString())) ? Type.EVENT_TYPE.O_DISCONNECT_1.getType() : Type.EVENT_TYPE.O_DISCONNECT_2.getType();
                Type type = new Type(Type.SERVICE_TYPE.SPEECH, Type.REQUEST_TYPE.O);
                Id id = new Id(call.getCallID(), null);
                EventArg eventArg = new EventArg(call.getEndType());
                EventReportCall eventReportCall = new EventReportCall(id, type, eventType, eventArg);
                try{
                    Message eventReportRet = appClient.dispatch(eventReportCall.toMessage());
                    Action action = new Action(eventReportRet.getIE(CFIE.ACTION_IE));
                    logger.info("eventReportCall:"+" callId:"+call.getCallID()+ " eventType:"+eventType+" result:"+action.getSessionAction().name());
                }catch (Exception ex){
                    logger.warning("eventReportCall Exception:"+" callId:"+call.getCallID()+ " eventType:"+eventType+" "+ex.getMessage());
                } 

                Integer endValue=0;
                if (call.getEndDate()!=null && call.getStartDate()!=null)
                    endValue=(int)(call.getEndDate().getTime()-call.getStartDate().getTime());
                long watchArg1=0l;
                WatchArg watchArg = new WatchArg(endValue,watchArg1, null, null, null, null);
                WatchReportCall watchReportCall = new WatchReportCall(id, Type.WATCH_TYPE.TIME_WATCH.getType(), null, watchArg);
                try{
                    Message watchReportRet = appClient.dispatch(watchReportCall.toMessage());
                    Action action = new Action(watchReportRet.getIE(CFIE.ACTION_IE));
                    logger.info("watchReportCall:"+" callId:"+call.getCallID()+ " watchType:"+Type.WATCH_TYPE.TIME_WATCH.getType()+" endValue:"+endValue+" watchArg1:"+watchArg1+" result:"+action.getSessionAction().name());
                }
                catch (Exception ex){
                    logger.warning("watchReportCall Exception:"+" callId:"+call.getCallID()+ " watchType:"+Type.WATCH_TYPE.TIME_WATCH.getType()+" "+ex.getMessage());
                }
            }
        }

        else if (call.getStatus()==Call.CALL_KILLED){
            logger.info("CALL KILLED. callID:"+call.getCallID()+" status:"+call.getStatus());
            call.setEndType(700);
            call.end(700);
        }

        else if (call.getStatus()==Call.CALL_CANCELLED){
            logger.info("CALL CANCELLED. callID:"+call.getCallID()+" status:"+call.getStatus());

            call.setEndType(SipServletResponse.SC_REQUEST_TERMINATED);
            byte result=0;
            Id id = new Id(call.getCallID(), null);
            Type type = new Type(Type.SERVICE_TYPE.SPEECH, Type.REQUEST_TYPE.O);
            EventArg eventArg = new EventArg(call.getEndType());
            EventReportCall eventReportCall = new EventReportCall(id, type, Type.EVENT_TYPE.O_ABANDON_1.getType(), eventArg);
            try{
                Message eventReportRet = appClient.dispatch(eventReportCall.toMessage());
                Action action = new Action(eventReportRet.getIE(CFIE.ACTION_IE));
                logger.info("eventReportCall:"+" callId:"+call.getCallID()+ " eventType:"+Type.EVENT_TYPE.O_ABANDON_1+" result:"+action.getSessionAction().name());
            }catch (Exception ex){
                logger.warning("eventReportCall Exception:"+" callId:"+call.getCallID()+ " eventType:"+Type.EVENT_TYPE.O_ANSWER_2+" "+ex.getMessage());
            } 

        }

        else if (call.getStatus()==Call.CALL_ON_HOLD){
            logger.info("CALL ON HOLD. callID:"+call.getCallID()+" status:"+call.getStatus());
        }

        else if (call.getStatus()==Call.CALL_RETRIEVED){
            logger.info("CALL RETRIEVED. callID:"+call.getCallID()+" status:"+call.getStatus());
            //logger.log(Level.INFO, "{0} RETRIEVED", call.getCallID());
        }
    }

    public String getRegex() {
        //This EventListener class will handle the call if the RURI in the initial INVITE matches this regex.
        return "^\\w{3}:\\d{4}591\\d{8}@.+$";
    }
}
