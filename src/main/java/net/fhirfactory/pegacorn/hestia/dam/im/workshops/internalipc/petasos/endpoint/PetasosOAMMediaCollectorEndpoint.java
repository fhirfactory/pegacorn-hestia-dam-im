/*
 * Copyright (c) 2021 Mark A. Hunter (ACT Health)
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
package net.fhirfactory.pegacorn.hestia.dam.im.workshops.internalipc.petasos.endpoint;

import ca.uhn.fhir.rest.api.MethodOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import net.fhirfactory.pegacorn.core.interfaces.media.PetasosMediaServiceClientWriterInterface;
import net.fhirfactory.pegacorn.core.interfaces.media.PetasosMediaServiceHandlerInterface;
import net.fhirfactory.pegacorn.core.interfaces.capabilities.CapabilityFulfillmentInterface;
import net.fhirfactory.pegacorn.core.interfaces.capabilities.CapabilityProviderNameServiceInterface;
import net.fhirfactory.pegacorn.core.interfaces.topology.ProcessingPlantInterface;
import net.fhirfactory.pegacorn.core.model.capabilities.base.CapabilityUtilisationRequest;
import net.fhirfactory.pegacorn.core.model.capabilities.base.CapabilityUtilisationResponse;
import net.fhirfactory.pegacorn.core.model.capabilities.base.factories.MethodOutcomeFactory;
import net.fhirfactory.pegacorn.core.model.capabilities.valuesets.WorkUnitProcessorCapabilityEnum;
import net.fhirfactory.pegacorn.core.model.topology.endpoints.edge.jgroups.JGroupsIntegrationPointSummary;
import net.fhirfactory.pegacorn.core.model.topology.role.ProcessingPlantRoleEnum;
import net.fhirfactory.pegacorn.core.model.transaction.factories.PegacornTransactionMethodOutcomeFactory;
import net.fhirfactory.pegacorn.core.model.transaction.model.PegacornTransactionMethodOutcome;
import net.fhirfactory.pegacorn.core.model.transaction.model.PegacornTransactionOutcome;
import net.fhirfactory.pegacorn.core.model.transaction.model.SimpleResourceID;
import net.fhirfactory.pegacorn.core.model.transaction.valuesets.PegacornTransactionStatusEnum;
import net.fhirfactory.pegacorn.core.model.transaction.valuesets.PegacornTransactionTypeEnum;
import net.fhirfactory.pegacorn.hestia.dam.im.workshops.datagrid.AsynchronousWriterMediaCache;
import net.fhirfactory.pegacorn.petasos.endpoints.services.media.PetasosMediaServicesEndpoint;
import net.fhirfactory.pegacorn.util.FHIRContextUtility;
import org.hl7.fhir.r4.model.Media;
import org.hl7.fhir.r4.model.IdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PetasosOAMMediaCollectorEndpoint extends PetasosMediaServicesEndpoint
        implements  PetasosMediaServiceHandlerInterface, CapabilityFulfillmentInterface {
    private static final Logger LOG = LoggerFactory.getLogger(PetasosOAMMediaCollectorEndpoint.class);

    @Inject
    private PetasosMediaServiceClientWriterInterface mediaWriter;

    @Inject
    private PegacornTransactionMethodOutcomeFactory outcomeFactory;

    @Inject
    private AsynchronousWriterMediaCache mediaCache;

    @Inject
    private MethodOutcomeFactory methodOutcomeFactory;

    @Inject
    private CapabilityProviderNameServiceInterface capabilityProviderNameServiceInterface;

    //
    // Constructor(s)
    //

    public PetasosOAMMediaCollectorEndpoint(){
        super();
    }

    //
    // Post Construct (invoked from Superclass)
    //

    @Override
    protected void executePostConstructInstanceActivities(){
        registerCapability();
    }

    //
    // Getters (and Setters)
    //

    @Override
    protected Logger specifyLogger() {
        return (LOG);
    }

    //
    // Media RPC Method Support
    //

    @Override
    public Boolean logMediaHandler(Media media, JGroupsIntegrationPointSummary sourceJGroupsIP){
        getLogger().debug(".logMediaHandler(): Entry, media->{}, sourceJGroupsIP->{}", media, sourceJGroupsIP);
        MethodOutcome outcome = null;
        if((media != null)) {
            getLogger().debug(".logMediaHandler(): Media is not -null-, writing it to the DM");
            outcome = mediaWriter.writeMediaSynchronously(media);
        }
        Boolean success = false;
        if(outcome != null){
            if(outcome.getCreated()){
                success = true;
            }
        }
        getMetricsAgent().incrementRemoteProcedureCallHandledCount();
        getLogger().debug(".logMediaHandler(): Exit, success->{}", success);
        return(success);
    }

    @Override
    public Boolean logMediaAsynchronouslyHandler(Media media, JGroupsIntegrationPointSummary jgroupsIP) {
        getLogger().debug(".logMediaAsynchronouslyHandler(): Entry, media->{}, sourceJGroupsIP->{}", media, jgroupsIP);
        Boolean success = false;
        if(media != null) {
            getLogger().debug(".logMediaAsynchronouslyHandler(): Media is not -null-, adding it to queue");
            mediaCache.addMedia(media);
            success = true;
        }
        getMetricsAgent().incrementRemoteProcedureCallHandledCount();
        getLogger().debug(".logMediaAsynchronouslyHandler(): Exit, success->{}", success);
        return(success);
    }

    @Override
    public Boolean logMultipleMediaHandler(List<Media> mediaList, JGroupsIntegrationPointSummary jgroupsIP){
        getLogger().debug(".logMultipleMediaHandler(): Entry, eMediaList->{}, jgroupsIP->{}", mediaList, jgroupsIP);
        Boolean success = false;
        if(mediaList != null) {
            getLogger().debug(".logMultipleMediaHandler(): MediaList is not -null-, adding entries to queue");
            for (Media currentMedia : mediaList) {
                mediaCache.addMedia(currentMedia);
            }
        }
        success = true;
        getMetricsAgent().incrementRemoteProcedureCallHandledCount();
        getLogger().debug(".logMultipleMediaHandler(): Exit, success->{}", success);
        return(success);
    }

    //
    // Capability Execution Service
    //

    public void registerCapability(){
        getProcessingPlant().registerCapabilityFulfillmentService(WorkUnitProcessorCapabilityEnum.CAPABILITY_INFORMATION_MANAGEMENT_MEDIA.getDisplayName(), this);
    }

    public CapabilityUtilisationResponse executeTask(CapabilityUtilisationRequest request) {
        String mediaAsString = request.getRequestStringContent();
        MethodOutcome methodOutcome = null;
        try {
            Media media = getFHIRJSONParser().parseResource(Media.class, mediaAsString);
            methodOutcome = mediaWriter.writeMediaSynchronously(media);
        } catch (Exception ex){
            methodOutcome = new MethodOutcome();
            methodOutcome.setCreated(false);
        }
        String simpleOutcomeAsString = null;
        PegacornTransactionOutcome simpleOutcome = new PegacornTransactionOutcome();
        SimpleResourceID resourceID = new SimpleResourceID();
        if(methodOutcome.getCreated()) {
            if(methodOutcome.getId() != null) {
                if (methodOutcome.getId().hasResourceType()) {
                    resourceID.setResourceType(methodOutcome.getId().getResourceType());
                } else {
                    resourceID.setResourceType("Media");
                }
                resourceID.setValue(methodOutcome.getId().getValue());
                if (methodOutcome.getId().hasVersionIdPart()) {
                    resourceID.setVersion(methodOutcome.getId().getVersionIdPart());
                } else {
                    resourceID.setVersion(SimpleResourceID.DEFAULT_VERSION);
                }
                simpleOutcome.setResourceID(resourceID);
            }
            simpleOutcome.setTransactionStatus(PegacornTransactionStatusEnum.CREATION_FINISH);
        } else {
            simpleOutcome.setTransactionStatus(PegacornTransactionStatusEnum.CREATION_FAILURE);
        }
        simpleOutcome.setTransactionType(PegacornTransactionTypeEnum.CREATE);
        simpleOutcome.setTransactionSuccessful(methodOutcome.getCreated());
        CapabilityUtilisationResponse response = new CapabilityUtilisationResponse();
        response.setInstantCompleted(Instant.now());
        response.setAssociatedRequestID(request.getRequestID());
        return(response);
    }
}
