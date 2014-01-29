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
import com.nuevatel.common.helper.StringHelper;
import com.nuevatel.common.helper.xml.XmlHash;
import com.nuevatel.common.helper.xml.XmlHashImpl;
import com.nuevatel.sip.SipCommand;
import com.nuevatel.sip.SipHeaders;
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
import javax.servlet.ServletConfig;
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
import javax.servlet.sip.SipSession.State;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.TimerListener;
import javax.servlet.sip.TimerService;
import javax.servlet.sip.TooManyHopsException;
import javax.servlet.sip.UAMode;
import javax.servlet.sip.URI;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import static com.nuevatel.sip.voipcarrier.helper.VoipConstants.*;


/**
 * 
 * @author luis
 */
@javax.servlet.sip.annotation.SipServlet(loadOnStartup = 1)
@javax.servlet.sip.annotation.SipListener
public class VoIPCarrierServlet extends SipServlet
        implements SipApplicationSessionListener, TimerListener{

    private static final String XPATH_VOIPCARRIER_PROPERTIES = "//config/properties/property[@name='voipcarrier.properties']";

    private static final String XPATH_APPCLIENT_PROPERTIES = "//config/properties/property[@name='app-client.properties']";

    /**
     * Serial version for the servlet.
     */
    private static final long serialVersionUID = 290102014L;

    /**
     * Global configuration. Indicate the location for the *properties.
     */
    private static final String CONFIG_XML = "config.xml";

    /**
     * Relative root path.
     */
    public static final String ROOT_PATH = "/";

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

    private ServletTimer sTimer = null;

    @Resource
    private TimerService tService;

    public VoIPCarrierServlet(){
        // No op
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        logger.trace("Executing init method");

        try{

            XmlHash confXmlHash = getConfigXmlHash();
            String voipCarrierPropPath = confXmlHash.getFirstNode(XPATH_VOIPCARRIER_PROPERTIES).getNodeValue();
            String appClientPropPath = confXmlHash.getFirstNode(XPATH_APPCLIENT_PROPERTIES).getNodeValue();

            if (StringHelper.isBlank(voipCarrierPropPath) || StringHelper.isBlank(appClientPropPath)) {

                throw new Exception(String.format(
                        "Invalid path of properties files. voipcarrier.properties: %s"
                        + " app-client.properties: %s. Check config.xml file.",
                        voipCarrierPropPath, appClientPropPath));
            }

            //load properties
            logger.trace(String.format("Loading voipcarrier properties from: %s", voipCarrierPropPath));
            voipCarrierProperties = loadProperties(voipCarrierPropPath);

            logger.trace(String.format("Loading appclient properties from: %s", appClientPropPath));
            appClientProperties = loadProperties(appClientPropPath);

            TaskSet taskSet = new TaskSet();
            taskSet.add(CFMessage.TEST_SESSION_CALL, new TestSessionCallTask());
            taskSet.add(CFMessage.SET_SESSION_CALL, new SetSessionCallTask());

            // Load all properties for voipcarrier, in serverlet context variables.
            loadVoipCarrierProperties();

            appClient = new AppClient(localId, remoteId, taskSet, appClientProperties);
            appClient.start();

            logger.info(String.format(
                    "Startted AppClient localId: %d remoteId: %d address: %s port: %s size: %s",
                    localId, remoteId, appClientProperties.getProperty("address"),
                    appClientProperties.getProperty("port"), appClientProperties.getProperty("size")));

        } catch(Exception ex) {
            logger.error("The servlet cannot be initialized.", ex);
        }
    }

    @Override
    public void destroy(){
        try{
            logger.trace("It is detroying.");
            appClient.interrupt();

        } catch(Exception ex) {
            logger.error("When the appClient was interrupted.", ex);
        }
    }

    @Override
    protected void doRequest (SipServletRequest request) throws IOException, ServletException{
        logger.trace(String.format("doRequest is executing. Session ID: %s Method: %s",
                request.getSession().getId(), request.getMethod()));

        Call call = getCall(request);
        String method = request.getMethod();

        if (request.isInitial() || SipCommand.ACK.isSameCmd(method) || SipCommand.CANCEL.isSameCmd(method)) {
            //Handled by the normal doXXX methods
            super.doRequest(request);
        } else if (call.getStatus()<Call.MEDIA_CALL_INITIALIZED){
            B2buaHelper b2b = request.getB2buaHelper();
            SipSession linked = b2b.getLinkedSession(request.getSession());

            if (linked!=null){
                SipServletRequest other = b2b.createRequest(linked, request, null);
                copyContent(request, other);
                copyHeaders(request, other);
                other.send();
            }
            else{
                 request.createResponse(SipServletResponse.SC_OK).send();
            }

            super.doRequest(request);
        }

    }

    @Override
    protected void doResponse (SipServletResponse response) throws IOException, ServletException{
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

                // TODO Remove me!!
                logger.debug("Call.CALL_ENDED");
                // TODO Stop servlet timer
                // stopServletTimer(response.getSession());
                super.doResponse(response);
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
                copyHeaders(response, other);

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

    @Override
    protected void doInvite (SipServletRequest request) throws IOException, TooManyHopsException{
        logger.trace(String.format("doInvite is executing. Session ID: %s", request.getSession().getId()));
        URI caller = request.getFrom().getURI();
        URI callee = request.getTo().getURI();

        if (request.isInitial()) {
            // If is an initial request.
            logger.trace(String.format("A call is initializing. URICaller: %s URICallee: %s",
                    caller, callee));

            String payer = ((SipURI) caller).getUser();
            SipFactory sipFactory = (SipFactory) getServletContext().getAttribute(SIP_FACTORY);
            Call call = new Call(request.getApplicationSession().getId(), caller, callee, payer, request.getRequestURI(), sipFactory, request);
            call.setRemoteHost(request.getRemoteHost());
            call.setRemotePort(request.getRemotePort());
            call.addListener(new VoIPCarrierListener(cellGlobalId, carrierPrefixSize, hubbingPrefix));
            request.getApplicationSession().setAttribute(CONTEXT_ATTR_CALL, call);
            call.setAppClient(appClient);
            call.setAnonymousMask(anonymousMask);
            CacheHandler.getCacheHandler().getCallsMap().put(call.getCallID(), call);
            call.setStatus(Call.CALL_INITIALIZED);

            //INITIALIZED event fires at this moment
            if (call.getStatus() != Call.CALL_ENDED && call.getStatus() < Call.MEDIA_CALL_INITIALIZED) {//if event hasn't cause the call to end and there is no media session
                B2buaHelper b2b = request.getB2buaHelper();
                SipServletRequest other = b2b.createRequest(request, true, null);
                copyContent(request, other);
                other.setRequestURI(call.getCallee());
                other.getFrom().setURI(call.getCaller());
                other.getTo().setURI(call.getCallee());
                copyHeaders(request, other);
                other.send();

                // Initialize the timmer.
                startServletTimmer(request.getApplicationSession(), 20000, 20000,
                        request.getSession().getId(), request.getSession());
            }

        } else {
            // If the request already exist.
            Call call = getCall(request);

            if (call.isOnHold()) {
                logger.trace(String.format("OnHold disable. URICaller: %s URICallee: %s", caller, callee));

                call.setOnHold(false);
                call.setStatus(Call.CALL_RETRIEVED);
            } else {
                logger.trace(String.format("OnHold enable. URICaller: %s URICallee: %s", caller, callee));

                call.setOnHold(true);
                call.setStatus(Call.CALL_ON_HOLD);
            }
        }
    }

    @Override
    protected void doAck(SipServletRequest request) throws IOException{
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
                    copyHeaders(request, ack);
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

    @Override
    protected void doCancel(SipServletRequest request) throws IOException{
        logger.trace(String.format("doCancel is executing. Session ID: %s", request.getSession().getId()));

        Call call = (Call)request.getApplicationSession().getAttribute("call");
        call.setStatus(Call.CALL_CANCELLED);
        B2buaHelper b2b = request.getB2buaHelper();
        SipSession ss = b2b.getLinkedSession(request.getSession());
        SipServletRequest cancel = b2b.createCancel(ss);
        copyHeaders(request, cancel);
        cancel.send();

        // Stop timmer
        stopServletTimer(request.getSession());
    }

    @Override
    protected void doErrorResponse(SipServletResponse response) throws ServletException, IOException {
        logger.trace(String.format("doErrorResponse is executing. Session ID: %s", response.getSession().getId()));

        // Stop timmer
        stopServletTimer(response.getSession());

        super.doErrorResponse(response);
    }

    @Override
    protected void doBye(SipServletRequest request) throws ServletException, IOException {
        logger.trace(String.format("doBye is executing. Session ID: %s", request.getSession().getId()));

        Call call = getCall(request);
        call.setEndType(SipServletResponse.SC_OK);
        call.setEndRequestParty(request.getFrom().getURI());
        call.setEndDate(new Date());
        call.setStatus(Call.CALL_ENDED);

        // Stop timmer.
        stopServletTimer(request.getSession());

        super.doBye(request);
    }

     private Call getCall(SipServletMessage servletMessage) {
        Call call = (Call) servletMessage.getApplicationSession().getAttribute(CONTEXT_ATTR_CALL);
        return call;
    }

    /**
     *
     *
     * @param messageSource
     * @param otherMessage
     */
    private void copyHeaders(SipServletMessage messageSource, SipServletMessage otherMessage) {
        copyHeader(messageSource, otherMessage, SipHeaders.SUPPORTED);
        copyHeader(messageSource, otherMessage, SipHeaders.ALLOW);
        copyHeader(messageSource, otherMessage, SipHeaders.SESSION_EXPIRES);
        copyHeader(messageSource, otherMessage, SipHeaders.MIN_SE);
        copyHeader(messageSource, otherMessage, SipHeaders.REQUIRE);
    }

    private void copyHeader (SipServletMessage source, SipServletMessage target, SipHeaders sipHeader){
        int count=0;
        String headerName = sipHeader.toString();
        String header=null;
        for (Iterator<String> iterator = source.getHeaders(headerName.toString()); iterator.hasNext();){
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

    private BigDecimal getCallTimeSpan(SipSession session) {
        BigDecimal creationTime = new BigDecimal(session.getCreationTime());
        BigDecimal nowTime = new BigDecimal(new Date().getTime());
        BigDecimal timeSpan = creationTime.subtract(nowTime).abs();
        return timeSpan;
    }

    private void loadVoipCarrierProperties() throws NumberFormatException {
        //set variables
        localId = Integer.valueOf(voipCarrierProperties.getProperty("localId", "500"));
        remoteId = Integer.valueOf(voipCarrierProperties.getProperty("remoteId", "40"));
        carrierPrefixSize = Integer.valueOf(voipCarrierProperties.getProperty("carrierPrefixSize", "4"));
        hubbingPrefix = voipCarrierProperties.getProperty("hubbingPrefix");
        cellGlobalId = voipCarrierProperties.getProperty("cellGlobalId", "500000");
        anonymousMask = voipCarrierProperties.getProperty("anonymousMask", "70100000");
    }

    private XmlHash getConfigXmlHash() throws SAXException, ParserConfigurationException, IOException {
        // Load config.xml
        // Get global configuration.
        String relativePath = getServletContext().getRealPath(ROOT_PATH);
        String configFile = CONFIG_XML;
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        Document configDoc = builder.parse(String.format("%s%s", relativePath, configFile));
        XmlHash confXmlHash = new XmlHashImpl(configDoc);

        return confXmlHash;
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

        if (session != null && !State.TERMINATED.equals(session.getState())) {
            BigDecimal timeSpan = getCallTimeSpan(session);

            // TODO Time to log
            logger.debug(String.format(
                    "Time elapsed in milleseconds: %f for the call: %s ",
                    timeSpan.doubleValue(), session.getCallId()));
        } else {
            // TODO
            // sipSession.createRequest("BYE").send();
            logger.debug(String.format("session: %s does not exist.", (String) sTimer.getInfo()));
        }
    }

    // TODO replaced by dependency inyection.
//    private TimerService getTimerService() {
//        return (TimerService) getServletContext().getAttribute(SipServlet.TIMER_SERVICE);
//    }

    private void startServletTimmer(SipApplicationSession applicationSession,
                                    long startTime,
                                    long period,
                                    Serializable srlzblInfo,
                                    SipSession session) {
//        TimerService tService = getTimerService();
        // TODO Check if fixed-delay and is persistent can be get from config file.
        sTimer = tService.createTimer(applicationSession, startTime, period, true, false, srlzblInfo);

        logger.info(String.format(
                "Servlet Timer was created. Session ID: %s Call ID: %s Start Time: %s Period: %s",
                session.getId(), session.getCallId(), startTime, period));
    }

    private void stopServletTimer(SipSession session) {

        if (sTimer != null) {
            sTimer.cancel();

            BigDecimal timeSpan = getCallTimeSpan(session);

            logger.info(String.format("Call TimeSpan: %f SessionID: %s CallID:%s",
                    timeSpan.doubleValue(), session.getId(), session.getCallId()));
            logger.info(String.format("Servlet Timer was canceled. Session ID: %s Call ID: %s",
                    session.getId(), session.getCallId()));
        }
    }
}