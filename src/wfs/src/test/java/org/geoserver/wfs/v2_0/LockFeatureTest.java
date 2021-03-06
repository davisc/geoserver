/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wfs.v2_0;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;

import org.custommonkey.xmlunit.XMLUnit;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.wfs.WFSException;
import org.geotools.filter.v2_0.FES;
import org.geotools.wfs.v2_0.WFS;
import org.junit.Test;
import org.w3c.dom.Document;

import org.springframework.mock.web.MockHttpServletResponse;

public class LockFeatureTest extends WFS20TestSupport {

    @Override
    protected void setUpInternal(SystemTestData data) throws Exception {
        getServiceDescriptor20().getOperations().add( "ReleaseLock");
    }
        
	@Test
    public void testLock() throws Exception {
        String xml = 
            "<wfs:LockFeature xmlns:sf=\"http://cite.opengeospatial.org/gmlsf\" " +
            "   xmlns:wfs='" + WFS.NAMESPACE + "' expiry=\"5\" handle=\"LockFeature-tc1\" "
                + " lockAction=\"ALL\" "
                + " service=\"WFS\" "
                + " version=\"2.0.0\">"
                + "<wfs:Query handle=\"lock-1\" typeNames=\"sf:PrimitiveGeoFeature\"/>"
            + "</wfs:LockFeature>";

        Document dom = postAsDOM("wfs", xml);
        assertEquals("wfs:LockFeatureResponse", dom.getDocumentElement().getNodeName());
        assertEquals(5, dom.getElementsByTagNameNS(FES.NAMESPACE, "ResourceId").getLength());
        
        // release the lock
        print(dom);
        String lockId = dom.getDocumentElement().getAttribute("lockId");        
        get("wfs?request=ReleaseLock&version=2.0&lockId=" + lockId);
    }
	
	@Test
    public void testSOAP() throws Exception {
        String xml = 
           "<soap:Envelope xmlns:soap='http://www.w3.org/2003/05/soap-envelope'> " + 
                " <soap:Header/> " + 
                " <soap:Body>"
             +   "<wfs:LockFeature xmlns:sf=\"http://cite.opengeospatial.org/gmlsf\" "
             +   "   xmlns:wfs='" + WFS.NAMESPACE + "' expiry=\"5\" handle=\"LockFeature-tc1\" "
                    + " lockAction=\"ALL\" "
                    + " service=\"WFS\" "
                    + " version=\"2.0.0\">"
                    + "<wfs:Query handle=\"lock-1\" typeNames=\"sf:PrimitiveGeoFeature\"/>"
                + "</wfs:LockFeature>" + 
                " </soap:Body> " + 
            "</soap:Envelope> "; 
              
        MockHttpServletResponse resp = postAsServletResponse("wfs", xml, "application/soap+xml");
        assertEquals("application/soap+xml", resp.getContentType());
        
        Document dom = dom(new ByteArrayInputStream(resp.getContentAsString().getBytes()));
        assertEquals("soap:Envelope", dom.getDocumentElement().getNodeName());
        assertEquals(1, dom.getElementsByTagName("wfs:LockFeatureResponse").getLength());
        
        // release the lock
        String lockId = XMLUnit.newXpathEngine().evaluate("//wfs:LockFeatureResponse/@lockId", dom);
        get("wfs?request=ReleaseLock&version=2.0&lockId=" + lockId);
    }

    @Test
    public void testRenewLockFail() throws Exception {
        String xml =
                "<wfs:LockFeature xmlns:sf=\"http://cite.opengeospatial.org/gmlsf\" " +
                        "   xmlns:wfs='" + WFS.NAMESPACE + "' expiry=\"1\" handle=\"LockFeature-tc1\" "
                        + " lockAction=\"ALL\" "
                        + " service=\"WFS\" "
                        + " version=\"2.0.0\">"
                        + "<wfs:Query handle=\"lock-1\" typeNames=\"sf:PrimitiveGeoFeature\"/>"
                        + "</wfs:LockFeature>";

        Document dom = postAsDOM("wfs", xml);
        print(dom);
        assertEquals("wfs:LockFeatureResponse", dom.getDocumentElement().getNodeName());
        assertEquals(5, dom.getElementsByTagNameNS(FES.NAMESPACE, "ResourceId").getLength());
        String lockId = XMLUnit.newXpathEngine().evaluate("//wfs:LockFeatureResponse/@lockId", dom);
        
        // wait a couple of seconds, the lock was acquired only for one
        Thread.sleep(2 * 1000);
        
        // try to reset the expired lock, it should fail
        xml =
                "<wfs:LockFeature xmlns:sf=\"http://cite.opengeospatial.org/gmlsf\" " +
                        "   xmlns:wfs='" + WFS.NAMESPACE + "' expiry=\"1\" handle=\"LockFeature-tc1\" "
                        + " lockAction=\"ALL\" "
                        + " service=\"WFS\" "
                        + " version=\"2.0.0\"" 
                        + " lockId=\"" + lockId + "\"/>";
        MockHttpServletResponse response = postAsServletResponse("wfs", xml);
        assertEquals(403, response.getStatus());
        dom = dom(new ByteArrayInputStream(response.getContentAsByteArray()));
        checkOws11Exception(dom, "2.0.0", WFSException.LOCK_HAS_EXPIRED, "lockId");
    }

    @Test
    public void testRenewUnknownLock() throws Exception {
        // try to reset an unknown lock instead
        String xml =
                "<wfs:LockFeature xmlns:sf=\"http://cite.opengeospatial.org/gmlsf\" " +
                        "   xmlns:wfs='" + WFS.NAMESPACE + "' expiry=\"1\" handle=\"LockFeature-tc1\" "
                        + " lockAction=\"ALL\" "
                        + " service=\"WFS\" "
                        + " version=\"2.0.0\""
                        + " lockId=\"abcd\"/>";
        MockHttpServletResponse response = postAsServletResponse("wfs", xml);
        assertEquals(400, response.getStatus());
        Document dom = dom(new ByteArrayInputStream(response.getContentAsByteArray()));
        checkOws11Exception(dom, "2.0.0", WFSException.INVALID_LOCK_ID, "lockId");


    }

}
