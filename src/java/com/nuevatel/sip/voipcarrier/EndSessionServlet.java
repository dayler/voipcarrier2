/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.nuevatel.sip.voipcarrier;

import javax.servlet.sip.SipServletResponse;

/**
 * Dummy Servlet to serve the final 200 OK after call cut
 * @author luis
 */
@javax.servlet.sip.annotation.SipServlet(loadOnStartup=1)
public class EndSessionServlet extends javax.servlet.sip.SipServlet{
    @Override protected void doResponse (SipServletResponse response){
    }
}