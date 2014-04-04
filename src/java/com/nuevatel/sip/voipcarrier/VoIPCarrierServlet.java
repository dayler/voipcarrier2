/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier;

import java.util.concurrent.ScheduledExecutorService;
import com.nuevatel.cf.appconn.CFIE;
import com.nuevatel.cf.appconn.Action;
import com.nuevatel.base.appconn.Message;
import com.nuevatel.cf.appconn.CFIE.WATCH_TYPE;
import com.nuevatel.cf.appconn.Type;
import com.nuevatel.sip.voipcarrier.listener.VoIPCarrierListener;
import javax.servlet.sip.SipSessionEvent;
import javax.servlet.sip.SipSessionListener;
import com.nuevatel.base.appconn.AppClient;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipServlet;
import com.nuevatel.cf.appconn.CFMessage;
import com.nuevatel.base.appconn.TaskSet;
import com.nuevatel.common.helper.IntegerHelper;
import com.nuevatel.common.helper.StringHelper;
import com.nuevatel.common.helper.xml.XmlHash;
import com.nuevatel.sip.SipCommand;
import com.nuevatel.sip.SipHeaders;
import com.nuevatel.sip.exception.IllegalValueException;
import com.nuevatel.sip.voipcarrier.helper.ConfigHelper;
import com.nuevatel.sip.voipcarrier.listener.WatchReportRetEventResponse;
import com.nuevatel.sip.voipcarrier.listener.EventListenerResponseSet;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
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
import org.apache.log4j.Logger;
import com.nuevatel.cf.appconn.Id;
import com.nuevatel.cf.appconn.WatchArg;
import com.nuevatel.cf.appconn.WatchReportCall;
import com.nuevatel.sip.voipcarrier.worker.KillCallSessionWorker;
import java.math.RoundingMode;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static com.nuevatel.common.helper.ClassHelper.*;
import static com.nuevatel.sip.voipcarrier.helper.VoipConstants.*;


/**
 * Determines if the communication between caller and callee can be established (is in the network,
 * have enough credit) and establish it. Notify to VONE how long the call lasted.
 *
 * @author luis, asalazar
 */
@javax.servlet.sip.annotation.SipServlet(loadOnStartup = 1)
@javax.servlet.sip.annotation.SipListener
public class VoIPCarrierServlet extends SipServlet
        implements SipApplicationSessionListener, TimerListener, SipSessionListener{

    /**
     * Path to get enable test mode property.
     */
    private static final String XPATH_ENABLE_TEST_MODE = "//config/properties/property[@name='enable-test-mode']/text()";

    /**
     * Path to get the location of the voipcarrier configuration file.
     */
    private static final String XPATH_VOIPCARRIER_PROPERTIES = "//config/properties/property[@name='voipcarrier.properties']";

    /**
     * Path to get the location of the app conn configuration file.
     */
    private static final String XPATH_APPCLIENT_PROPERTIES = "//config/properties/property[@name='app-client.properties']";

    /**
     * Serial version for the servlet.
     */
    private static final long serialVersionUID = 290102014L;

    /**
     * Application logger.
     */
    private static Logger logger = Logger.getLogger(VoIPCarrierServlet.class);

    /**
     * Common application properties.
     */
    private Properties voipCarrierProperties = new Properties();

    /**
     * App conn properties. Used to establish the dialog with VONE.
     */
    private Properties appClientProperties = new Properties();

    /**
     * Client to stablish dialog with VONE.
     */
    private AppClient appClient;

    /**
     * Application local ID.
     */
    private int localId;

    /**
     * Remote ID with which the application is registered.
     */
    private int remoteId;

    /**
     * The size of the characters used to identify the carrier in the caller URI.
     */
    private int carrierPrefixSize;

    private String hubbingPrefix;

    /**
     * Used to communicate with VONE. It is a mock cell ID.
     */
    private String cellGlobalId;

    /**
     * Used to identify the caller in case it was unknown.
     */
    private String anonymousMask;

    /**
     * Servlet timer, execute periodical tasks to notify the status and duration of the calls.
     */
    private ServletTimer sTimer = null;

    /**
     * True if test mode is not enabled.
     */
    private boolean isNotEnableTestMode = false;

    /**
     *Executor thread used to execute schedules tasks.
     */
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);

    /**
     * Factory for the timers.
     */
    @Resource
    private TimerService tService;

    /**
     * {@inheritDoc}
     *
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        logger.info("Executing init method");

        try{

            String relativePath = getServletContext().getRealPath(ROOT_PATH);
            XmlHash confXmlHash = ConfigHelper.getConfigXmlHash(relativePath);
            String voipCarrierPropPath = confXmlHash.getFirstNode(XPATH_VOIPCARRIER_PROPERTIES).getNodeValue();
            String appClientPropPath = confXmlHash.getFirstNode(XPATH_APPCLIENT_PROPERTIES).getNodeValue();
            isNotEnableTestMode = !Boolean.parseBoolean(confXmlHash.get(XPATH_ENABLE_TEST_MODE));

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
            taskSet.add(CFMessage.SET_SESSION_CALL, new SetSessionCallTask());

            // Load all properties for voipcarrier, in serverlet context variables.
            loadVoipCarrierProperties();

            logger.info(String.format(
                    "Loaded VOIP Carrier poperties cellGlobalId: %s carrierPrefixSize: %s "
                    + "hubbingPrefix: %s anonymousMaks: %s"
                    , cellGlobalId, carrierPrefixSize, hubbingPrefix, anonymousMask));

            appClient = new AppClient(localId, remoteId, taskSet, appClientProperties);
            appClient.start();

            logger.info(String.format(
                    "Startted AppClient localId: %d remoteId: %d address: %s port: %s size: %s",
                    localId, remoteId, appClientProperties.getProperty("address"),
                    appClientProperties.getProperty("port"), appClientProperties.getProperty("size")));

        } catch(Throwable ex) {
            logger.error("The servlet cannot be initialized.", ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     */
    @Override
    public void destroy(){
        try{
            logger.info("It is detroying.");
            appClient.interrupt();

            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }

        } catch(Exception ex) {
            logger.error("When the appClient was interrupted.", ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     */
    @Override
    protected void doRequest (SipServletRequest request) throws IOException, ServletException{
        logger.info(String.format("doRequest is executing. Session ID: %s Method: %s",
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

    /**
     * {@inheritDoc}
     *
     */
    @Override
    protected void doResponse (SipServletResponse response) throws IOException, ServletException{
        SipSession session = response.getSession();
        logger.info(String.format("doResponse is executing. Session ID: %s command: %s Response Status: %d",
                session.getId(), response.getMethod(), response.getStatus()));

        // Instead call object just set the call id, and get the call instance usign cahehandler
        Call call = (Call)response.getApplicationSession().getAttribute("call");
        int responseStatus = response.getStatus();

        logger.trace(String.format("URICaller: %s URICallee: %s Response Status: %s callStatus: %s",
                response.getFrom().getURI(), response.getTo().getURI(), responseStatus, call.getStatus()));

        if (responseStatus == SipServletResponse.SC_REQUEST_TERMINATED){
            return; //487 already sent on Cancel for initial leg UAS
        }

        // TODO Check, what happens if the condition is negative
        if (call.getStatus() < Call.MEDIA_CALL_INITIALIZED) { //if there's no media session involved
            B2buaHelper b2b = response.getRequest().getB2buaHelper();
            SipSession linked = b2b.getLinkedSession(session);
            SipServletResponse other = null;

            if (responseStatus > SipServletResponse.SC_OK) {
                //final response. cut call
                call.setEndType(responseStatus);
                //call.setEndDate(new Date());
                call.setStatus(Call.CALL_ENDED);

                logger.debug(String.format("Call was ended. CallID: %s SessionID: %s",
                        session.getCallId(), session.getId()));
                super.doResponse(response);
            }

            if (response.getRequest().isInitial()) {
                // Handled separately due to possibility of forking and multiple SIP 200 OK responses
                other = b2b.createResponseToOriginalRequest(linked, responseStatus, response.getReasonPhrase());

            } else if (responseStatus != SipServletResponse.SC_NOT_FOUND && linked!=null) {
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
                } else {
                    other.send();
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     */
    @Override
    protected void doInvite (SipServletRequest request) throws IOException, TooManyHopsException{
        logger.info(String.format("doInvite is executing. Session ID: %s", request.getSession().getId()));
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

                if (isNotEnableTestMode) {
                    other.setRequestURI(call.getCallee()); // Modify the "callee"
                    other.getFrom().setURI(call.getCaller());
                    other.getTo().setURI(call.getCallee());
                }

                copyHeaders(request, other);

                if (logger.isTraceEnabled()) {
                    logger.trace(String.format(
                            "B2BUA is creating second request.%s Primary Request: %s  %s Second Request: %s",
                            StringHelper.END_LINE, request, StringHelper.END_LINE, other));
                }

                other.send();
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

    /**
     * {@inheritDoc}
     *
     */
    @Override
    protected void doAck(SipServletRequest request) throws IOException{
        logger.info(String.format("doAck is executing. Session ID: %s", request.getSession().getId()));

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

                    if (response.getRequest().isInitial()) {
                        Call call = getCall(request);

                        EventListenerResponseSet responseSet = call.setStatus(Call.CALL_STARTED);
                        WatchReportRetEventResponse eventResponse =
                                castAs(WatchReportRetEventResponse.class,
                                responseSet.getEventResponse(VoIPCarrierListener.LISTENER_NAME, Call.CALL_STARTED));

                        Integer watchPeriod = eventResponse.getWatchPeriod();
                        Integer watchOffset = eventResponse.getWatchOffset();

                        if (watchOffset != null) {
                            scheduleEndCallTask(call, watchOffset);
                        } else if (watchPeriod != null) {
                            // Start servlet timer.
                            startServletTimmer(watchPeriod, request);
                        } else {
                            // Log something and kill session.
                            logger.error(String.format("WatchReportRetEventResponse was not return."
                                    + " The call %s will be terminated.", call.getCallID()));
                            call.kill();
                        }
                    }
                }
            }
        }
    }

    /**
     * Schedule end call task.
     *
     * @param call The call to end.
     * @param watchOffset Time in milliseconds to wait before to end call.
     */
    private void scheduleEndCallTask(Call call, Integer watchOffset) {
        // Schedule kill task
        KillCallSessionWorker worker = new KillCallSessionWorker(call);
        long watchOffsetLong = new Long(watchOffset);
        executorService.schedule(worker, watchOffsetLong, TimeUnit.MILLISECONDS);
    }

    /**
     * {@inheritDoc}
     *
     */
    @Override
    protected void doCancel(SipServletRequest request) throws IOException{
        logger.info(String.format("doCancel is executing. Session ID: %s", request.getSession().getId()));

        Call call = (Call)request.getApplicationSession().getAttribute("call");
        call.setStatus(Call.CALL_CANCELLED);
        B2buaHelper b2b = request.getB2buaHelper();
        SipSession ss = b2b.getLinkedSession(request.getSession());
        SipServletRequest cancel = b2b.createCancel(ss);
        copyHeaders(request, cancel);
        cancel.send();

        // Stop timmer
        stopServletTimer(call, request.getSession());
    }

    /**
     * {@inheritDoc}
     *
     */
    @Override
    protected void doErrorResponse(SipServletResponse response) throws ServletException, IOException {
        logger.info(String.format("doErrorResponse is executing. Session ID: %s", response.getSession().getId()));
        // TODO Test if call can be retrieved.
        // Stop timmer
        Call call = getCall(response);
        stopServletTimer(call, response.getSession());

        super.doErrorResponse(response);
    }

    /**
     * {@inheritDoc}
     *
     */
    @Override
    protected void doBye(SipServletRequest request) throws ServletException, IOException {
        logger.info(String.format("doBye is executing. Session ID: %s", request.getSession().getId()));

        Call call = getCall(request);
        call.setEndType(SipServletResponse.SC_OK);
        call.setEndRequestParty(request.getFrom().getURI());
        //call.setEndDate(new Date());
        call.setStatus(Call.CALL_ENDED);

        // Stop timmer.
        stopServletTimer(call, request.getSession());

        super.doBye(request);
    }


    /**
     * {@inheritDoc}
     *
     */
    @Override
    public void sessionCreated(SipSessionEvent sse) {
        // No op. For log purposes.
        logger.trace("Session created.");
    }

    /**
     * {@inheritDoc}
     *
     */
    @Override
    public void sessionDestroyed(SipSessionEvent sse) {
        // No op. For log purposes.
        logger.trace("Session destroyed.");
    }

    /**
     * @inheritDoc
     */
    @Override
    public void sessionReadyToInvalidate(SipSessionEvent arg0) {
        // No op.
        logger.trace("Session ready to invalidate.");
    }

    /**
     * Execute WatchReportCall, it is used to notify to cf the time it is taking the call.
     *
     * @param call Call in progress.
     * @param timeSpan Time to notify.
     */
    private void doWatchReportCall(Call call, BigDecimal timeSpan) {
        logger.info("Execute doWatchReportCall");

        Id id = new Id(call.getCallID(), null);
        long watchArg1 = 0l;
        int timeSpanIntValue =
                timeSpan.setScale(0).divide(FIX_MILLISECONDS_FACTOR,
                RoundingMode.HALF_UP).intValueExact();
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
                call.kill();
                logger.info(String.format("Call: %s was killed.", call.getCallID()));
            } else {

                // Look into watch report rep.
                WatchReportRetEventResponse watchEventResponse = new WatchReportRetEventResponse(watchReportRet);

                if (watchEventResponse.getWatchOffset() != null) {
                    // Schedule kill call.
                    scheduleEndCallTask(call, watchEventResponse.getWatchOffset());
                }

                logger.info(String.format(
                        "watchReportCall: callId:%s watchType:%s watchTimeValue:%d watchArg1:%d result:%s",
                        call.getCallID(), typeTimeWatch.name(), timeSpanIntValue, watchArg1,
                        action.getSessionAction().name()));
            }
        } catch (Exception ex) {
            logger.error("Occurred while WatchReportCall is executing", ex);

            call.setStatus(Call.CALL_KILLED);
        }
    }

    /**
     * @param servletMessage Servlet message used to get the current session, and current call.
     *
     * @return Current call associated with the session.
     */
     private Call getCall(SipServletMessage servletMessage) {
        return getCall(servletMessage.getApplicationSession());
    }

     /**
      * @param appSession Used to get the current session, and current call.
      *
      * @return Current call associated with the session.
      */
    private Call getCall(SipApplicationSession appSession) {
        Call call = null;

        try {
            call = castAs(Call.class, appSession.getAttribute(CONTEXT_ATTR_CALL));
        } catch (IllegalStateException ex) {
            // Log exception in debug mode
            logger.debug("Call cannot be retrieved. Session was finalized.", ex);
        }

        return call;
    }

    /**
     * Copy needed headers from Message source to dest message.
     *
     * @param messageSource The source.
     *
     * @param otherMessage Dest Message.
     */
    private void copyHeaders(SipServletMessage messageSource, SipServletMessage otherMessage) {
        copyHeader(messageSource, otherMessage, SipHeaders.SUPPORTED);
        copyHeader(messageSource, otherMessage, SipHeaders.ALLOW);
        copyHeader(messageSource, otherMessage, SipHeaders.SESSION_EXPIRES);
        copyHeader(messageSource, otherMessage, SipHeaders.MIN_SE);
        copyHeader(messageSource, otherMessage, SipHeaders.REQUIRE);
    }

    /**
     * Copy a specific header from source servlet message to target.
     *
     * @param source Source Servlet Message.
     * @param target Target Servlet Message.
     * @param sipHeader The Header Name to copy.
     */
    private void copyHeader(SipServletMessage source, SipServletMessage target, SipHeaders sipHeader) {
        int count = 0;
        String headerName = sipHeader.toString();
        String header = null;
        for (Iterator<String> iterator = source.getHeaders(headerName.toString()); iterator.hasNext();) {
            count++;
            String tmpHeader = iterator.next();
            if (header == null) {
                header = tmpHeader;
            } else {
                header += tmpHeader;
            }
            if (count > 0 && iterator.hasNext()) {
                header += ", ";
            }
        }
        if (header != null) {
            target.setHeader(headerName, header);
            if (headerName.contains("Session-Expires")) {
                if (header.contains("refresher")) {
                    target.setHeader("Session-Expires", "100; refresher=uas");
                } else {
                    target.setHeader("Session-Expires", "100");
                }
            }
        }
    }

    /**
     *
     * @param call Current media call in progress.
     *
     * @return How long the call lasted, at the time in which this method is called.
     */
    private BigDecimal getCallTimeSpan(Call call) {
        Date referenceEndDate = new Date();

        if (call == null) {
            return BigDecimal.ZERO;
        }

        long referenceEndTime;

        if (!Call.CALL_ENDED.equals(call.getStatus()) || call.getEndDate() == null) {
            // If the call still in progress.
            referenceEndTime = referenceEndDate.getTime();
        } else {
            referenceEndTime = call.getEndDate().getTime();
        }

        BigDecimal creationTime = new BigDecimal(call.getStartDate().getTime());
        BigDecimal endTime = new BigDecimal(referenceEndTime);
        BigDecimal timeSpan = creationTime.subtract(endTime).abs();

        return timeSpan;
    }

    /**
     * Load voipcarrier common properties.
     *
     * @throws NumberFormatException When one or more properties that are expecting decimal numbers
     * are incorrect.
     */
    private void loadVoipCarrierProperties() throws NumberFormatException, IllegalValueException {
        //set variables
        localId =
                checkRequiredPorperty(IntegerHelper.tryParse(voipCarrierProperties.getProperty("localId")), "localId");
        remoteId =
                checkRequiredPorperty(IntegerHelper.tryParse(voipCarrierProperties.getProperty("remoteId")), "remoteId");
        carrierPrefixSize = IntegerHelper.tryParse(voipCarrierProperties.getProperty("carrierPrefixSize", "4"));
        hubbingPrefix = voipCarrierProperties.getProperty("hubbingPrefix", "0019");
        cellGlobalId = voipCarrierProperties.getProperty("cellGlobalId", "500000");
        anonymousMask = voipCarrierProperties.getProperty("anonymousMask", "70100000");
    }

    /**
     * Throw an exception when parameter to check is null. It means that the parameter must to be specified.
     *
     * @param <T> Type of the property to check.
     * @param value Value of the property to check.
     * @param propertyName Name of the property to check.
     *
     * @return Same value of the property passed as parameter, if it is not null.
     *
     * @throws IllegalValueException If the property value is null.
     */
    private <T> T checkRequiredPorperty(T value, String propertyName) throws IllegalValueException {
        if (value == null) {
            String msg = String.format("Property: %s cannot be empty.", propertyName);
            throw new IllegalValueException(msg);
        }

        return value;
    }

    /**
     * Copy the content of Servlet Message to other Servlet Message.
     *
     * @param source Source from copy.
     * @param destination Target to copy.
     *
     * @throws IOException When cannot be read or copy the content.
     */
    private void copyContent(SipServletMessage source, SipServletMessage destination) throws IOException {
        if (source.getContentLength()>0){
            destination.setContent(source.getContent(), source.getContentType());
            String encoding = source.getCharacterEncoding();
            if (encoding!=null && encoding.length()>0){
                destination.setCharacterEncoding(encoding);
            }
        }
    }

    /**
     * Read property file, from path.
     *
     * @param filePath Path to get the property file.
     *
     * @return Property object with the content of property file.
     *
     * @throws FileNotFoundException The file was not found.
     * @throws IOException The file cannot be read.
     */
    private Properties loadProperties(String filePath) throws FileNotFoundException, IOException{
        FileInputStream fis = null;

        try {
            File file = new File(filePath);
            Properties properties = new Properties();
            fis = new FileInputStream(file);
            properties.load(fis);

            return properties;
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     */
    @Override
    public void sessionCreated(SipApplicationSessionEvent ev) {
        // No op.
        logger.trace("Session created.");
    }

    /**
     * {@inheritDoc}
     *
     */
    @Override
    public void sessionDestroyed(SipApplicationSessionEvent ev) {
        // No op.
        logger.trace("Session destroyed.");
    }

    /**
     * {@inheritDoc}
     *
     */
    public void sessionExpired(SipApplicationSessionEvent ev) {
        SipApplicationSession appSession=ev.getApplicationSession();

        if (appSession.isValid()) {
            appSession.setExpires(3);
        }
    }

    /**
     * {@inheritDoc}
     *
     */
    public void sessionReadyToInvalidate(SipApplicationSessionEvent ev) {
        Call call = getCall(ev.getApplicationSession());
        CacheHandler.getCacheHandler().getCallsMap().remove(call.getCallID());
    }

    /**
     * @inheritDoc
     */
    @Override
    public void timeout(ServletTimer sTimer) {
        logger.trace("Time out is firing");

        SipApplicationSession applicationSession = sTimer.getApplicationSession();
        SipSession session = applicationSession.getSipSession((String) sTimer.getInfo());

        if (session != null && !State.TERMINATED.equals(session.getState())) {
            Call call = getCall(applicationSession);
            BigDecimal timeSpan = getCallTimeSpan(call);

            logger.info(String.format(
                    "Time elapsed in milleseconds: %f for the call: %s ",
                    timeSpan.doubleValue(), session.getCallId()));

            // Repoprt to VONE.
            doWatchReportCall(call, timeSpan);

        } else {
            logger.debug(String.format("session: %s does not exist.", (String) sTimer.getInfo()));
            sTimer.cancel();
        }
    }

    /**
     * Initialize the timer responsible to do the periodical notification to the cf.
     *
     * @param watchPeriod Period on milliseconds.
     * @param request 
     */
    private void startServletTimmer(Integer watchPeriod, SipServletRequest request) {
        if (watchPeriod != null) {
            SipSession session = request.getSession();
            // Start timer
            sTimer = tService.createTimer(request.getApplicationSession(),
                    0, // Start time
                    watchPeriod, // Period
                    true,
                    false,
                    session.getId()); // Serializable info.

            logger.info(String.format(
                    "Servlet Timer was created. Session ID: %s Call ID: %s Start Time: %s Period: %s",
                    session.getId(), session.getCallId(), watchPeriod, watchPeriod));
        }
    }

    /**
     * Interrupt the timer, it is used when the call was stop
     *
     * @param call Call in progress.
     * @param session Sip session.
     */
    private void stopServletTimer(Call call, SipSession session) {

        BigDecimal timeSpan = BigDecimal.ZERO;

        if (sTimer != null) {
            sTimer.cancel();


            timeSpan = getCallTimeSpan(call);
        }

        logger.info(String.format("Call TimeSpan: %f SessionID: %s CallID:%s",
                    timeSpan.doubleValue(), session.getId(), session.getCallId()));
            logger.info(String.format("Servlet Timer was canceled. Session ID: %s Call ID: %s",
                    session.getId(), session.getCallId()));
    }
}
