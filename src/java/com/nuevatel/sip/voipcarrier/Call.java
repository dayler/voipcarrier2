/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier;

import com.nuevatel.base.appconn.AppClient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import javax.servlet.ServletException;
import javax.servlet.sip.ServletParseException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.URI;
import org.apache.log4j.Logger;

/**
 *
 * @author luis
 */

public class Call {
    public static final Integer CALL_NONEXISTENT = 1;
    public static final Integer CALL_INITIALIZED = 2;
    public static final Integer CALL_STARTED     = 3;
    public static final Integer CALL_ENDED       = 4;
    public static final Integer CALL_CANCELLED   = 5;
    public static final Integer CALL_ON_HOLD     = 6;
    public static final Integer CALL_RETRIEVED   = 7;
    public static final Integer CALL_KILLED      = 8; //Only when RTBS kills call
    public static final Integer CALL_SEQUESTERED = 9;
    public static final Integer MEDIA_CALL_INITIALIZED          = 51;
    public static final Integer MEDIA_CALL_STARTED              = 52;
    public static final Integer MEDIA_CALL_REQUEST_SENT         = 53;
    public static final Integer MEDIA_CALL_RECEIVED_DIGITS      = 54;
    public static final Integer MEDIA_CALL_ENDED                = 55;
    public static final Integer MEDIA_CALL_JOINING_PARTY        = 56; //Internal use, Do not override in eventListener
    public static final Integer MEDIA_CALL_JOINING_PARTY_PR     = 57; //Internal use, Do not override in eventListener
    public static final Integer MEDIA_CALL_JOINING_PARTY_ERROR  = 58;
    public static final Integer MEDIA_CALL_CONNECTING_CALL      = 59;

    /**
     * Application logger
     */
    private final static Logger logger = Logger.getLogger(Call.class);

    private ArrayList<EventListener> listenersList = new ArrayList<EventListener>();

    private Integer status;
    private Integer subStatus;
    private Integer pastStatus;
    private URI caller;
    private URI callee;
    private String payer;
    private URI requestURI;
    private String id;
    private SipApplicationSession sas;
    SipFactory sipFactory;
    private VoIPCarrierServlet source;
    private Integer endType;
    private String lastDigits;
    private SipServletRequest initialRequest;
    private URI endRequestParty;
    private Date startDate=null;
    private Date endDate=null;
    private boolean onHold=false;
    private AppClient appClient;
    private String anonymousMaks;
    private String remoteHost;
    private Integer remotePort;

    public Call(String id, URI caller, URI callee, String payer, URI requestURI, SipFactory sipFactory, SipServletRequest initialRequest){
        this.id=id;
        this.caller = caller;
        this.callee = callee;
        this.payer = payer;
        this.requestURI=requestURI;
        this.sipFactory=sipFactory;
        this.initialRequest=initialRequest;

        this.status = Call.CALL_NONEXISTENT;
        this.pastStatus = Call.CALL_NONEXISTENT;
        this.subStatus=-1;
        this.endRequestParty=caller;
    }

    public void setStatus(Integer status){
//        try{
//            throw new Exception ("Caller is:");
//        }
//        catch (Exception e){
//            System.out.println("Class: "+e.getStackTrace()[1].getClassName());
//            System.out.println("Method: "+e.getStackTrace()[1].getMethodName());
//            System.out.println("Current status: "+this.status);
//            System.out.println("New status: "+status);
//        }
        if (status!=this.status){
            try {
                this.pastStatus = this.status;
                this.status = status;
//                ConversationEvent event = new ConversationEvent(this);
//                fireEvent(event);
            } catch (Exception ex) {
               // Logger.getLogger(Call.class.getName()).log(Level.SEVERE, null, ex);
                logger.error("The status of call cannot be changed.",ex);
            }
        }
    }

    public void setSubStatus(Integer subStatus){
        this.subStatus=subStatus;
    }

    public void setCaller(String caller) throws ServletParseException{
        this.caller = sipFactory.createURI(caller);
    }

    public void setCallee(String callee) throws ServletParseException{
        this.callee = sipFactory.createURI(callee);
    }

    public void setPayer(String payer){
        this.payer = payer;
    }

    public void setEndType(Integer endType){
        this.endType=endType;
    }

    public void setLastDigits(String digits){
        this.lastDigits = digits;
    }

    public void setMediaSession (SipSession mediaSession){
        this.sas.setAttribute("mediaSession", mediaSession);
    }

    public Integer getEndType (){
        return this.endType;
    }

    public Integer getStatus (){
        return this.status;
    }

    public Integer getSubStatus(){
        return this.subStatus;
    }

    public Integer getPastStatus(){
        return this.pastStatus;
    }
    
    public String getCallID(){
        return this.id;
    }

    public URI getCaller(){
        return this.caller;
    }

    public String getPayer(){
        return this.payer;
    }

    public URI getCallee(){
        return this.callee;
    }
    
    public URI getRURI(){
        return this.requestURI;
    }

    public String getLastDigits(){
        return this.lastDigits;
    }

    public SipSession getMediaSession(){
        SipSession mediaSession = (SipSession)sas.getAttribute("mediaSession");
        return mediaSession;
    }

    public SipApplicationSession getSipApplicationSession(){
        return this.sas;
    }

    public SipServletRequest getInitialRequest(){
        return this.initialRequest;
    }

    public void end(Integer statusCode){
        this.setStatus(Call.CALL_ENDED);
        if (getPastStatus()==Call.CALL_INITIALIZED){
            try {
                initialRequest.createResponse(statusCode).send();
            } catch (IOException ex) {
                logger.error("InitialRequest cannot create a response. ", ex);
            }
        }
        else if (getPastStatus() == Call.CALL_STARTED || getPastStatus() == Call.CALL_KILLED || getPastStatus() >= Call.MEDIA_CALL_INITIALIZED){

            Iterator i = initialRequest.getApplicationSession().getSessions();
            while (i.hasNext()){
                Object o = i.next();
                if (o instanceof SipSession){
                    SipSession ss = (SipSession)o;
                    SipServletRequest bye = ss.createRequest("BYE");
                    try {
                        ss.setHandler("EndSessionServlet");
                        bye.send();
                    } catch (IOException ex) {
                        logger.error("SipServletRequest cannot be sended. ", ex);

                    } catch (ServletException e){
                        logger.error("SipServletRequest cannot be sended. ", e);
                    }
                }
            }
        }
        CacheHandler.getCacheHandler().getCallsMap().remove(id);
    }
    public void setEndRequestParty(URI endRequestParty){
        this.endRequestParty=endRequestParty;
    }
    public URI getEndRequestParty(){
        return this.endRequestParty;
    }

    /**
     * @return the startDate
     */
    public Date getStartDate() {
        return startDate;
    }

    /**
     * @param startDate the startDate to set
     */
    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    /**
     * @return the endDate
     */
    public Date getEndDate() {
        return endDate;
    }

    /**
     * @param endDate the endDate to set
     */
    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }
    public void addListener(EventListener listener){
        listenersList.add(listener);
    }

    public void removeListener(EventListener listener){
        listenersList.remove(listener);
    }

    public boolean isOnHold(){
        return onHold;
    }

    public void setOnHold(boolean onHold){
        this.onHold=onHold;
    }


    public synchronized void fireEvent(ConversationEvent event) throws Exception{
        for (EventListener listener : listenersList){
            listener.eventReceived(event);
        }
    }

    /**
     * @return the cfClient
     */
    public AppClient getAppClient() {
        return appClient;
    }

    /**
     * @param appClient the cfClient to set
     */
    public void setAppClient(AppClient appClient) {
        this.appClient = appClient;
    }

    public String getAnonymousMask(){
        return anonymousMaks;
    }

    public void setAnonymousMask(String anonymousMask){
        this.anonymousMaks=anonymousMask;
    }

    /**
     * @return the remoteHost
     */
    public String getRemoteHost() {
        return remoteHost;
    }

    /**
     * @param remoteHost the remoteHost to set
     */
    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    /**
     * @return the remotePort
     */
    public Integer getRemotePort() {
        return remotePort;
    }

    /**
     * @param remotePort the remotePort to set
     */
    public void setRemotePort(Integer remotePort) {
        this.remotePort = remotePort;
    }
}