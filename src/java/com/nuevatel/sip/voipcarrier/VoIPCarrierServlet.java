/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier;
import com.nuevatel.base.appconn.AppClient;;
import com.nuevatel.cf.appconn.CFMessage;
import com.nuevatel.base.appconn.TaskSet;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
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
import javax.servlet.sip.TooManyHopsException;
import javax.servlet.sip.UAMode;
import javax.servlet.sip.URI;


/**
 *
 * @author luis
 */
@javax.servlet.sip.annotation.SipServlet(loadOnStartup=1)
@javax.servlet.sip.annotation.SipListener
public class VoIPCarrierServlet extends javax.servlet.sip.SipServlet implements SipApplicationSessionListener{

    private static final long serialVersionUID = 3978425801979081269L;
    //Reference to context - The ctx Map is used as a central storage for this app
    javax.servlet.ServletContext ctx = null;

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

    public VoIPCarrierServlet(){
        System.out.println("starting!");
    }
    
    @Override public void init(javax.servlet.ServletConfig config) throws javax.servlet.ServletException {
        super.init(config);
        ctx = config.getServletContext();
        System.out.println("executing init method!");
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
            System.out.println("started AppClient localId:"+localId+" remoteId:"+remoteId +" address:"+appClientProperties.getProperty("address")+" port:"+appClientProperties.getProperty("port")+" size:"+appClientProperties.getProperty("size"));
//            appClient = new CFClient(localId, cfClientProperties, actionCollection);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override public void destroy(){
        try{
            appClient.interrupt();
        }
        catch(Exception ex){
            ex.printStackTrace();
        }
    }

    @Override protected void doRequest (SipServletRequest request) throws IOException, ServletException{
        Call call = (Call)request.getApplicationSession().getAttribute("call");
        if (request.isInitial() || request.getMethod().equals("ACK") || request.getMethod().equals("CANCEL")) {
            super.doRequest(request); //Handled by the normal doXXX methods
        }
        //Logic is call-status based. for clarity's sake
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

            if (request.getMethod().equals("BYE")){
                call.setEndType(SipServletResponse.SC_OK);
                call.setEndRequestParty(request.getFrom().getURI());
                call.setEndDate(new Date());
                call.setStatus(Call.CALL_ENDED);
                
            }
            else if (request.getMethod().equals("INVITE") && !request.isInitial()){
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

    @Override protected void doResponse (SipServletResponse response) throws IOException{
        Call call = (Call)response.getApplicationSession().getAttribute("call");
        if (response.getStatus() == SipServletResponse.SC_REQUEST_TERMINATED) return; //487 already sent on Cancel for initial leg UAS
        if (call.getStatus() < Call.MEDIA_CALL_INITIALIZED) { //if there's no media session involved
            B2buaHelper b2b = response.getRequest().getB2buaHelper();
            SipSession linked = b2b.getLinkedSession(response.getSession());
            SipServletResponse other = null;
            if (response.getStatus() > SipServletResponse.SC_OK) {
                //final response. cut call
                call.setEndType(response.getStatus());
                call.setEndDate(new Date());
                call.setStatus(Call.CALL_ENDED);
                
            }
            if (response.getRequest().isInitial()) {
                // Handled separately due to possibility of forking and multiple SIP 200 OK responses
                other = b2b.createResponseToOriginalRequest(linked, response.getStatus(), response.getReasonPhrase());
            }
            else if (response.getStatus() != SipServletResponse.SC_NOT_FOUND && linked!=null) {
                //Other responses than to initial request
                SipServletRequest otherReq = b2b.getLinkedSipServletRequest(response.getRequest());
                other = otherReq.createResponse(response.getStatus(), response.getReasonPhrase());
                
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
                        ex.printStackTrace();
                    }
                }
                else {
                    other.send();
                }
            }
        }
    }
    
    @Override protected void doInvite (SipServletRequest request) throws IOException, TooManyHopsException{
        
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
        }
    }
    @Override protected void doAck(SipServletRequest request) throws IOException{
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
    }

//    public void endCall(Call call, int statusCode){
//        if (call.getPastStatus()==Call.CALL_INITIALIZED){
//            SipServletRequest initialRequest = call.getInitialRequest();
//            try {
//                initialRequest.createResponse(statusCode).send();
//            } catch (IOException ex) {
//                ex.printStackTrace();
//            }
//        }
//        else if (call.getPastStatus() == Call.CALL_STARTED || call.getPastStatus() == Call.CALL_KILLED || call.getPastStatus() >= Call.MEDIA_CALL_INITIALIZED){
//            Iterator i = call.getSipApplicationSession().getSessions();
//            while (i.hasNext()){
//                Object o = i.next();
//                if (o instanceof SipSession){
//                    SipSession ss = (SipSession)o;
//                    SipServletRequest bye = ss.createRequest("BYE");
//                    try {
//                        ss.setHandler("EndSessionServlet");
//                        bye.send();
//                    } catch (IOException ex) {
//                        ex.printStackTrace();
//                    } catch (ServletException e){
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }
//    }

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
}