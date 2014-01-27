/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier;
import com.nuevatel.base.appconn.AppClient;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipServlet;
import com.nuevatel.cf.appconn.CFMessage;
import com.nuevatel.base.appconn.TaskSet;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.sip.B2buaHelper;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipApplicationSessionEvent;
import javax.servlet.sip.SipApplicationSessionListener;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.TimerListener;
import javax.servlet.sip.TimerService;
import javax.servlet.sip.TooManyHopsException;
import javax.servlet.sip.UAMode;
import javax.servlet.sip.URI;
import org.apache.log4j.Logger;


/**
 * 
 * @author luis
 */
@javax.servlet.sip.annotation.SipServlet(loadOnStartup = 1)
@javax.servlet.sip.annotation.SipListener
public class VoIPCarrierServlet extends SipServlet
        implements SipApplicationSessionListener, TimerListener{

    private static final long serialVersionUID = 3978425801979081269L;

    /**
     * Application logger.
     */
    private static Logger logger = Logger.getLogger(VoIPCarrierServlet.class);

    private Properties voipCarrierProperties = new Properties();

    private Properties appClientProperties = new Properties();

    private AppClient appClient;

    private int localId;

    private int remoteId;

    private int carrierPrefixSize;

    private String hubbingPrefix;

    private String cellGlobalId;

    private String anonymousMask;

    private ArrayList<String> trustedServers = new ArrayList<String>();

    private ServletTimer sTimer = null;

    @Resource
    private TimerService tService;

    public VoIPCarrierServlet(){
        // No op
    }
    
    @Override
    public void init(javax.servlet.ServletConfig config) throws javax.servlet.ServletException {
        super.init(config);

        logger.trace("executing init method");

        try{
            //load properties
            voipCarrierProperties = loadProperties("/cf/properties/voipcarrier.properties");
            appClientProperties = loadProperties(voipCarrierProperties.getProperty("appClientProperties"));

            TaskSet taskSet = new TaskSet();
            taskSet.add(CFMessage.TEST_SESSION_CALL, new TestSessionCallTask());
            taskSet.add(CFMessage.SET_SESSION_CALL, new SetSessionCallTask());

            //set variables
            localId=Integer.valueOf(voipCarrierProperties.getProperty("localId","500"));
            remoteId=Integer.valueOf(voipCarrierProperties.getProperty("remoteId","40"));
            carrierPrefixSize = Integer.valueOf(voipCarrierProperties.getProperty("carrierPrefixSize","4"));
            hubbingPrefix = voipCarrierProperties.getProperty("hubbingPrefix");
            cellGlobalId = voipCarrierProperties.getProperty("cellGlobalId","500000");
            anonymousMask = voipCarrierProperties.getProperty("anonymousMask","70100000");
            String servers = voipCarrierProperties.getProperty("trustedServers");

            if (servers!=null){
                for (String server : servers.split("\\s+")){
                    trustedServers.add(server);
                }   
            }

            System.out.println("Trusted Servers:"+trustedServers);

            appClient = new AppClient(localId, remoteId, taskSet, appClientProperties);
            appClient.start();

            logger.info(String.format(
                    "Startted AppClient localId: %d remoteId: %d address: %s port: %s size: %s",
                    localId, remoteId, appClientProperties.getProperty("address"),
                    appClientProperties.getProperty("port"), appClientProperties.getProperty("size")));

        }
        catch(Exception e){
            logger.error("The servlet cannot be initialized.", e);
        }
    }

    @Override
    public void destroy(){
        try{
            logger.trace("It is detroying.");
            appClient.interrupt();
        }
        catch(Exception ex){
            logger.error("When the appClient is interrupting.", ex);
        }
    }

    @Override
    protected void doRequest (SipServletRequest request) throws IOException, ServletException{
        logger.trace(String.format("doRequest is executing. Session ID: %s", request.getSession().getId()));

        // TODO Instead call object just set the call id, and get the call instance usign cahehandler
        Call call = (Call)request.getApplicationSession().getAttribute("call");

        if (request.isInitial() || request.getMethod().equals("ACK") || request.getMethod().equals("CANCEL")) {
            super.doRequest(request); //Handled by the normal doXXX methods
        }
        // Logic is call-status based. for clarity's sake
        // TODO Check, what happens if the condition is negative
        else if (call.getStatus()<Call.MEDIA_CALL_INITIALIZED){ //if there's no media session involved
            B2buaHelper b2b = request.getB2buaHelper();
            SipSession linked = b2b.getLinkedSession(request.getSession());

            if (linked!=null){
                SipServletRequest other = b2b.createRequest(linked, request, null);
                copyContent(request, other);
                copyHeader(request, other, "Supported");
                copyHeader(request, other, "Allow");
                copyHeader(request, other, "Session-Expires");
                copyHeader(request, other, "Min-SE");
                copyHeader(request, other, "Require");
                other.send();
            }
            else{
                 request.createResponse(SipServletResponse.SC_OK).send();
            }

            if (request.getMethod().equals("BYE")) {
                call.setEndType(SipServletResponse.SC_OK);
                call.setEndRequestParty(request.getFrom().getURI());
                call.setEndDate(new Date());
                call.setStatus(Call.CALL_ENDED);
                // TODO Stop Servlet Listener
                // stopServletTimer(request.getSession());
            }
            else if (request.getMethod().equals("INVITE") && !request.isInitial()){
                // TODO Move this logic to do invite.
                if (!call.isOnHold()){
                    call.setOnHold(true);
                    call.setStatus(Call.CALL_ON_HOLD);
                }
                else{
                    call.setOnHold(false);
                    call.setStatus(Call.CALL_RETRIEVED);
                }
            }
        }
    }

    @Override
    protected void doResponse (SipServletResponse response) throws IOException{
        logger.trace(String.format("doResponse is executing. Session ID: %s", response.getSession().getId()));

        // TODO Instead call object just set the call id, and get the call instance usign cahehandler
        Call call = (Call)response.getApplicationSession().getAttribute("call");
        int responseStatus = response.getStatus();

        if (responseStatus == SipServletResponse.SC_REQUEST_TERMINATED){
            return; //487 already sent on Cancel for initial leg UAS
        }

        // TODO Check, what happens if the condition is negative
        if (call.getStatus() < Call.MEDIA_CALL_INITIALIZED) { //if there's no media session involved
            B2buaHelper b2b = response.getRequest().getB2buaHelper();
            SipSession linked = b2b.getLinkedSession(response.getSession());
            SipServletResponse other = null;
            if (responseStatus > SipServletResponse.SC_OK) {
                //final response. cut call
                call.setEndType(responseStatus);
                call.setEndDate(new Date());
                call.setStatus(Call.CALL_ENDED);
                // TODO Stop servlet timer
                // stopServletTimer(response.getSession());
                
            }
            if (response.getRequest().isInitial()) {
                // Handled separately due to possibility of forking and multiple SIP 200 OK responses
                other = b2b.createResponseToOriginalRequest(linked, responseStatus, response.getReasonPhrase());
            }
            else if (responseStatus != SipServletResponse.SC_NOT_FOUND && linked!=null) {
                //Other responses than to initial request
                SipServletRequest otherReq = b2b.getLinkedSipServletRequest(response.getRequest());
                other = otherReq.createResponse(responseStatus,response.getReasonPhrase());
                
            }
            if (other != null) {
                copyContent(response, other);
                copyHeader(response, other, "Supported");
                copyHeader(response, other, "Allow");
                copyHeader(response, other, "Session-Expires");
                copyHeader(response, other, "Min-SE");
                copyHeader(response, other, "Require");
                if ((other.getStatus()==SipServletResponse.SC_SESSION_PROGRESS || other.getStatus()==SipServletResponse.SC_CALL_BEING_FORWARDED) && response.getHeader("Require")!=null && response.getHeader("RSeq")!=null){
                    try {
                        other.sendReliably();
                    }catch (Exception ex){
                        logger.error("SipServletResponse can not be send reliably", ex);
                    }
                }
                else {
                    other.send();
                }
            }
        }
    }
    
    @Override protected void doInvite (SipServletRequest request) throws IOException, TooManyHopsException{
        logger.trace(String.format("doInvite is executing. Session ID: %s", request.getSession().getId()));

        // TODO Remove it, it is not longer necesary.
        //for testing only
        if (((SipURI)request.getFrom().getURI()).getUser().contains("70710200")){
            ((SipURI)request.getRequestURI()).setUser("1002"+((SipURI)request.getRequestURI()).getUser());
            ((SipURI)request.getTo().getURI()).setUser("1002"+((SipURI)request.getTo().getURI()).getUser());
            ((SipURI)request.getRequestURI()).setHost("10.20.3.80");
            ((SipURI)request.getTo().getURI()).setHost("10.20.3.80");
            request.getApplicationSession().setExpires(1);
        }

        URI caller = request.getFrom().getURI();
        URI callee = request.getTo().getURI();
        String payer = ((SipURI)caller).getUser();
        SipFactory sipFactory = (SipFactory)getServletContext().getAttribute(SIP_FACTORY);
        Call call = new Call(request.getApplicationSession().getId(), caller, callee, payer, request.getRequestURI(), sipFactory, request);
        call.setRemoteHost(request.getRemoteHost());
        call.setRemotePort(request.getRemotePort());
        call.addListener(new  VoIPCarrierListener(cellGlobalId, carrierPrefixSize, hubbingPrefix));
        request.getApplicationSession().setAttribute("call", call);
        call.setAppClient(appClient);
        call.setAnonymousMask(anonymousMask);
        CacheHandler.getCacheHandler().getCallsMap().put(call.getCallID(), call);
        call.setStatus(Call.CALL_INITIALIZED);

        //INITIALIZED event fires at this moment
        if (call.getStatus()!=Call.CALL_ENDED && call.getStatus() < Call.MEDIA_CALL_INITIALIZED){//if event hasn't cause the call to end and there is no media session
            B2buaHelper b2b = request.getB2buaHelper();
            SipServletRequest other = b2b.createRequest(request, true, null);
            copyContent(request, other);
            other.setRequestURI(call.getCallee());
            other.getFrom().setURI(call.getCaller());
            other.getTo().setURI(call.getCallee());

            if (!isRequestTrusted(other, trustedServers)){

                String privacy = other.getHeader("Privacy");
                if (privacy!=null && !privacy.contains("none")){
                    other.removeHeader("P-Asserted-Identity");
                    other.removeHeader("Privacy");
                }
            }
            copyHeader(request, other, "Supported");
            copyHeader(request, other, "Allow");
            copyHeader(request, other, "Session-Expires");
            copyHeader(request, other, "Min-SE");
            copyHeader(request, other, "Require");
            other.send();

            // TODO Initialize the timmer.
            startServletTimmer(request.getApplicationSession(), 20000, 20000,
                    request.getSession().getId(), request.getSession());
        }
    }

//    @Override
//    public void doRegister (SipServletRequest sipReq) throws IOException, ServletParseException {
//        //saveContact(sipReq);// Comienza a obtener el tiempo de session.
//        sipReq.createResponse(SipServletResponse.SC_ACCEPTED).send();
//    }

    @Override protected void doAck(SipServletRequest request) throws IOException{
        logger.trace(String.format("doAck is executing. Session ID: %s", request.getSession().getId()));

        B2buaHelper b2b = request.getB2buaHelper();
        SipSession ss = b2b.getLinkedSession(request.getSession());
        List<SipServletMessage> msgs = b2b.getPendingMessages(ss, UAMode.UAC);
        for (SipServletMessage message:msgs){
            if (message instanceof SipServletResponse){
                SipServletResponse response = (SipServletResponse)message;
                if (response.getStatus()==SipServletResponse.SC_OK){
                    SipServletRequest ack = response.createAck();
                    copyContent(request, ack);
                    copyHeader(request, ack, "Supported");
                    copyHeader(request, ack, "Allow");
                    copyHeader(request, ack, "Session-Expires");
                    copyHeader(request, ack, "Min-SE");
                    copyHeader(request, ack, "Require");
                    ack.send();
                    if (response.getRequest().isInitial()){
                        Call call = (Call)request.getApplicationSession().getAttribute("call");
                        // if there is a media session initialized, start a media session, otherwise start a call
                        if (call.getStatus()==Call.MEDIA_CALL_INITIALIZED) call.setStatus(Call.MEDIA_CALL_STARTED);
                        else{
                            call.setStartDate(new Date());
                            call.setStatus(Call.CALL_STARTED);
                        }
                    }
                }
            }
        }
    }

    @Override protected void doCancel(SipServletRequest request) throws IOException{
        logger.trace(String.format("doCancel is executing. Session ID: %s", request.getSession().getId()));

        Call call = (Call)request.getApplicationSession().getAttribute("call");
        call.setStatus(Call.CALL_CANCELLED);
        B2buaHelper b2b = request.getB2buaHelper();
        SipSession ss = b2b.getLinkedSession(request.getSession());
        SipServletRequest cancel = b2b.createCancel(ss);
        copyHeader(request, cancel, "Supported");
        copyHeader(request, cancel, "Allow");
        copyHeader(request, cancel, "Session-Expires");
        copyHeader(request, cancel, "Min-SE");
        copyHeader(request, cancel, "Require");
        cancel.send();

        // TODO Stop timmer
        stopServletTimer(request.getSession());
    }

    @Override
    protected void doErrorResponse(SipServletResponse response) throws ServletException, IOException {
        logger.trace(String.format("doErrorResponse is executing. Session ID: %s", response.getSession().getId()));

        // TODO Stop timmer
        stopServletTimer(response.getSession());

        super.doErrorResponse(response);
    }

    @Override
    protected void doBye(SipServletRequest request) throws ServletException, IOException {
        logger.trace(String.format("doBye is executing. Session ID: %s", request.getSession().getId()));

        // TODO Stop timer
        stopServletTimer(request.getSession());

        super.doBye(request);
    }



    private void copyContent(SipServletMessage source, SipServletMessage destination) throws IOException {
        if (source.getContentLength()>0){
            destination.setContent(source.getContent(), source.getContentType());
            String encoding = source.getCharacterEncoding();
            if (encoding!=null && encoding.length()>0){
                destination.setCharacterEncoding(encoding);
            }
        }
    }

    private Properties loadProperties(String filePath) throws FileNotFoundException, IOException{
        File file = new File(filePath);
        Properties properties = new Properties();
        FileInputStream fis = new FileInputStream(file);
        properties.load(fis);
        return properties;
    }
    
    private boolean isRequestTrusted(SipServletRequest request, ArrayList<String> trustedServers){
        boolean result = false;
        String pAssertedIdentity = request.getHeader("P-Asserted-Identity");
        if (pAssertedIdentity!=null){
            for (String server : trustedServers){
                if (pAssertedIdentity.contains(server)) result=true;
            }
        }
        return result;
    }
    
    private void copyHeader (SipServletMessage source, SipServletMessage target, String headerName){
        int count=0;
        String header=null;
        for (Iterator<String> iterator = source.getHeaders(headerName); iterator.hasNext();){
            count++;
            String tmpHeader = iterator.next();
            if (header==null) header=tmpHeader;
            else header+=tmpHeader;
            if (count>0 && iterator.hasNext()){
                header+=", ";
            }
        }
        if (header!=null){
            target.setHeader(headerName, header);
            if (headerName.contains("Session-Expires")){
            if (header.contains("refresher")) target.setHeader("Session-Expires", "100; refresher=uas");
            else target.setHeader("Session-Expires", "100");
        }
        }
        
    }

    public void sessionCreated(SipApplicationSessionEvent ev) {
    }

    public void sessionDestroyed(SipApplicationSessionEvent ev) {
    }

    public void sessionExpired(SipApplicationSessionEvent ev) {
        SipApplicationSession sas=ev.getApplicationSession();
        if (sas.isValid()) sas.setExpires(3);
    }

    public void sessionReadyToInvalidate(SipApplicationSessionEvent ev) {
        Call call = (Call)ev.getApplicationSession().getAttribute("call");
        CacheHandler.getCacheHandler().getCallsMap().remove(call.getCallID());
    }

    /**
     * @inheritDoc
     */
    @Override
    public void timeout(ServletTimer sTimer) {
        // TODO Here is the code to notify the appcon with the delta time.
        SipSession session = sTimer.getApplicationSession().getSipSession((String) sTimer.getInfo());
        BigDecimal creationTime = new BigDecimal(session.getCreationTime());
        BigDecimal nowTime = new BigDecimal(new Date().getTime());
        BigDecimal timeSpan = creationTime.subtract(nowTime).abs();

        // TODO Time to log
        logger.debug(String.format(
                "Time elapsed in milleseconds: %l for the call: %s ",
                timeSpan, session.getCallId()));
    }

    // TODO replaced by dependency inyection.
//    private TimerService getTimerService() {
//        return (TimerService) getServletContext().getAttribute(SipServlet.TIMER_SERVICE);
//    }

    private void startServletTimmer(SipApplicationSession applicationSession,
                                    long startTime,
                                    long period,
                                    Serializable srlzbl,
                                    SipSession session) {
//        TimerService tService = getTimerService();
        // TODO Check if fixed-delay and is persistent can be get from config file.
        sTimer = tService.createTimer(applicationSession, startTime, period, true, false, srlzbl);

        logger.info(String.format(
                "Servlet Timer was created. Session ID: %s Call ID: %d Start Time: %l Period: %l",
                session.getId(), session.getCallId(), startTime, period));
    }

    private void stopServletTimer(SipSession session) {
        
        if (sTimer != null) {
            sTimer.cancel();
            logger.info(String.format("Servlet Timer was canceled. Session ID: %s Call ID: %s",
                    session.getId(), session.getCallId()));
        }
    }
}