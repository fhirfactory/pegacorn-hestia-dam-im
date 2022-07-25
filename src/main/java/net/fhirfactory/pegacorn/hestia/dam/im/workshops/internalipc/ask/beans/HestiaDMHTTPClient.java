/*
 * Copyright (c) 2021 Mark A. Hunter
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.fhirfactory.pegacorn.hestia.dam.im.workshops.internalipc.ask.beans;

import java.util.ArrayList;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hl7.fhir.r4.model.Media;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.rest.api.MethodOutcome;
import net.fhirfactory.pegacorn.core.constants.systemwide.PegacornReferenceProperties;
import net.fhirfactory.pegacorn.core.interfaces.topology.ProcessingPlantInterface;
import net.fhirfactory.pegacorn.core.model.componentid.TopologyNodeFDN;
import net.fhirfactory.pegacorn.core.model.topology.endpoints.adapters.HTTPClientAdapter;
import net.fhirfactory.pegacorn.core.model.topology.endpoints.base.IPCTopologyEndpoint;
import net.fhirfactory.pegacorn.core.model.topology.endpoints.http.HTTPClientTopologyEndpoint;
import net.fhirfactory.pegacorn.core.model.topology.nodes.external.ConnectedExternalSystemTopologyNode;
import net.fhirfactory.pegacorn.deployment.topology.manager.TopologyIM;
import net.fhirfactory.pegacorn.hestia.dam.im.common.HestiaIMNames;
import net.fhirfactory.pegacorn.hestia.dam.im.processingplant.configuration.HestiaMediaIMTopologyFactory;
import net.fhirfactory.pegacorn.petasos.core.moa.wup.MessageBasedWUPEndpointContainer;
import net.fhirfactory.pegacorn.platform.edge.ask.base.http.InternalFHIRClientProxy;

@ApplicationScoped
public class HestiaDMHTTPClient extends InternalFHIRClientProxy {
    private static final Logger LOG = LoggerFactory.getLogger(HestiaDMHTTPClient.class);

    @Inject
    HestiaMediaIMTopologyFactory topologyFactory;

    @Inject
    private HestiaIMNames hestiaIMNames;

    @Override
    protected Logger getLogger() {
        return (LOG);
    }

    @Inject
    private TopologyIM topologyIM;

    @Inject
    private PegacornReferenceProperties systemWideProperties;

    @Inject
    private ProcessingPlantInterface processingPlant;

    public HestiaDMHTTPClient(){
        super();
        getLogger().info(".HestiaDMHTTPClient(): Starting");
    }

    @Override
    protected String deriveTargetEndpointDetails(){
        getLogger().debug(".deriveTargetEndpointDetails(): Entry");
        MessageBasedWUPEndpointContainer endpoint = new MessageBasedWUPEndpointContainer();
        HTTPClientTopologyEndpoint clientTopologyEndpoint = getTopologyEndpoint(hestiaIMNames.getInteractHestiaDMHTTPClientName());
        String endpointDetails = null;
        if(clientTopologyEndpoint != null) {
            if(clientTopologyEndpoint.getTargetSystem() != null) {
                ConnectedExternalSystemTopologyNode targetSystem = clientTopologyEndpoint.getTargetSystem();
                if(!targetSystem.getTargetPorts().isEmpty()) {
                    HTTPClientAdapter externalSystemIPCAdapter = (HTTPClientAdapter) targetSystem.getTargetPorts().get(0);
                    if(externalSystemIPCAdapter != null) {
                        String http_type = null;
                        if (externalSystemIPCAdapter.isEncrypted()) {
                            http_type = "https";
                        } else {
                            http_type = "http";
                        }
                        String dnsName = externalSystemIPCAdapter.getHostName();
                        String portNumber = Integer.toString(externalSystemIPCAdapter.getPortNumber());
                        endpointDetails = http_type + "://" + dnsName + ":" + portNumber + systemWideProperties.getPegacornInternalFhirResourceR4Path();
                    }
                }
            }
        }
        if (endpointDetails == null) {
            getLogger().error(".deriveTargetEndpointDetails(): Could not derive EndpointDetails for HestiaDM");
        }
        getLogger().debug(".deriveTargetEndpointDetails(): Exit, endpointDetails --> {}", endpointDetails);
        return (endpointDetails);

    }

    protected HTTPClientTopologyEndpoint getTopologyEndpoint(String topologyEndpointName){
        getLogger().debug(".getTopologyEndpoint(): Entry, topologyEndpointName->{}", topologyEndpointName);
        ArrayList<TopologyNodeFDN> endpointFDNs = processingPlant.getMeAsASoftwareComponent().getEndpoints();
        for(TopologyNodeFDN currentEndpointFDN: endpointFDNs){
            IPCTopologyEndpoint endpointTopologyNode = (IPCTopologyEndpoint)topologyIM.getNode(currentEndpointFDN);
            if(endpointTopologyNode.getEndpointConfigurationName().contentEquals(topologyEndpointName)){
                HTTPClientTopologyEndpoint clientTopologyEndpoint = (HTTPClientTopologyEndpoint)endpointTopologyNode;
                getLogger().debug(".getTopologyEndpoint(): Exit, node found -->{}", clientTopologyEndpoint);
                return(clientTopologyEndpoint);
            }
        }
        getLogger().error(".getTopologyEndpoint(): Exit, Could not find node for topologyEndpointName->{}", topologyEndpointName);
        return(null);
    }
    
    //XXX is this method needed?
    public MethodOutcome writeMedia(String mediaJSONString){
        getLogger().debug(".writeMedia(): Entry, mediaJSONString->{}", mediaJSONString);
        MethodOutcome outcome = null;
        try {
                getLogger().debug(".writeMedia(): Writing to Hestia-Media-DM");
                // write the media to the Persistence service
                Media media = getFHIRContextUtility().getJsonParser().parseResource(Media.class, mediaJSONString);
                outcome = writeMedia(media);
        } catch(Exception ex){
            getLogger().warn(".writeMedia(): Could not write Media, message->{}", ExceptionUtils.getMessage(ex));
            outcome = new MethodOutcome();
            outcome.setCreated(false);
        }
        getLogger().debug(".writeMedia(): Exit, outcome->{}", outcome);
        return(outcome);
    }

    public MethodOutcome writeMedia(Media media){
        getLogger().debug(".writeMedia(): Entry, media->{}", media);
        MethodOutcome outcome = null;
        try {
            getLogger().debug(".writeMedia(): client: ->{}", getClient().getClass());
            outcome = getClient().create()
                    .resource(media)
                    .prettyPrint()
                    .encodedJson()
                    .execute();

        } catch (Exception ex) {
            getLogger().error(".writeMedia(): ", ex);
            outcome = new MethodOutcome();
            outcome.setCreated(false);
        }
        getLogger().debug(".writeMedia(): Exit, outcome->{}", outcome);
        return(outcome);
    }
    
    public Media readMedia(String mediaId) {
        getLogger().debug(".readMedia(): Entry, mediaId->{}", mediaId);
        Media media = getClient().read().resource(Media.class).withId(mediaId).execute();
        getLogger().debug(".readMedia(): Exit, media->{}", media);
    	return media;
    }

    @Override
    protected void postConstructActivities() {

    }
}
