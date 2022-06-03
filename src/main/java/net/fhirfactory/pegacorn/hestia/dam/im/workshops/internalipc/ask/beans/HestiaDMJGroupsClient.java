/*
 * The MIT License
 *
 * Copyright 2021 Mark A. Hunter (ACT Health).
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package net.fhirfactory.pegacorn.hestia.dam.im.workshops.internalipc.ask.beans;

import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.api.MethodOutcome;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.fhirfactory.pegacorn.core.interfaces.topology.ProcessingPlantInterface;
import net.fhirfactory.pegacorn.core.model.capabilities.base.CapabilityUtilisationRequest;
import net.fhirfactory.pegacorn.core.model.capabilities.base.CapabilityUtilisationResponse;
import net.fhirfactory.pegacorn.core.model.dataparcel.DataParcelManifest;
import net.fhirfactory.pegacorn.core.model.petasos.uow.UoW;
import net.fhirfactory.pegacorn.core.model.petasos.uow.UoWPayload;
import net.fhirfactory.pegacorn.core.model.petasos.uow.UoWProcessingOutcomeEnum;
import net.fhirfactory.pegacorn.core.model.transaction.model.PegacornTransactionOutcome;
import net.fhirfactory.pegacorn.core.model.transaction.model.SimpleResourceID;
import net.fhirfactory.pegacorn.petasos.endpoints.services.tasking.CapabilityUtilisationBroker;
import net.fhirfactory.pegacorn.util.FHIRContextUtility;
import org.apache.camel.Exchange;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hl7.fhir.r4.model.Media;
import org.hl7.fhir.r4.model.IdType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.util.UUID;

/**
 *
 * @author Mark A. Hunter
 */
@ApplicationScoped
public class HestiaDMJGroupsClient {
    
    private static final Logger LOG = LoggerFactory.getLogger(HestiaDMJGroupsClient.class);
    
    private boolean initialised;
    private ObjectMapper jsonMapper;
    private IParser fhirParser;
    
    private static final String MEDIA_PERSISTENCE_DATA_MANAGER = "aether-hestia-dam-im";
    
    @Inject
    private ProcessingPlantInterface processingPlant;
    
    @Inject
    private FHIRContextUtility fhirContextUtility;

    @Inject
    private CapabilityUtilisationBroker capabilityUtilisationBroker;
    
  
    //
    // Constructor(s)
    // 
    
    public HestiaDMJGroupsClient(){
        setInitialised(false);
    }
    
    //
    // Post Constructor(s)
    //
    
    @PostConstruct
    public void initialise(){
        getLogger().debug(".initialise(): Entry");
        if(isInitialised()){
            getLogger().debug(".initialise(): Exit, already initialised!");
        } else {
            fhirParser = fhirContextUtility.getJsonParser();
            setInitialised(true);
        }
    }
    
    
    //
    // Business Methods
    //
    
    public UoW persistMedia(UoW uow, Exchange camelExchange){
        getLogger().debug(".persistMedia(): Entry, uow->{}", uow);
        
        String mediaString = uow.getIngresContent().getPayload();
        if(StringUtils.isEmpty(mediaString)){
            uow.setProcessingOutcome(UoWProcessingOutcomeEnum.UOW_OUTCOME_FAILED);
            uow.setFailureDescription("UoW Does Not Contain a Payload");
            return(uow);
        }

        Media media = (Media) fhirParser.parseResource(mediaString);
        
        if(media == null){
            uow.setProcessingOutcome(UoWProcessingOutcomeEnum.UOW_OUTCOME_FAILED);
            uow.setFailureDescription("Cannot convert UoW Payload into Media!");
            return(uow); 
        }
        
        MethodOutcome outcome = writeMediaIntoDM(media);
        
        String outcomeAsString = null;
        try {
            outcomeAsString = getJSONMapper().writeValueAsString(outcome);
        } catch (JsonProcessingException ex) {
            getLogger().warn(".persistMedia(): Cannot convert outcome to String, exception->{}", ExceptionUtils.getStackTrace(ex));
            uow.setProcessingOutcome(UoWProcessingOutcomeEnum.UOW_OUTCOME_FAILED);
            uow.setFailureDescription(ExceptionUtils.getStackTrace(ex));
            return(uow);
        }
        UoWPayload egressPayload = new UoWPayload();
        DataParcelManifest ingresManifest = uow.getIngresContent().getPayloadManifest();
        DataParcelManifest egressManifest = SerializationUtils.clone(ingresManifest);
        egressManifest.getContentDescriptor().setDataParcelDiscriminatorType("RESTfulAPI");
        egressManifest.getContentDescriptor().setDataParcelDiscriminatorValue("MethodOutcome");
        egressPayload.setPayload(outcomeAsString);
        egressPayload.setPayloadManifest(egressManifest);
        uow.getEgressContent().addPayloadElement(egressPayload);
        
        getLogger().debug(".persistMedia(): Exit, uow->{}", uow);
        return(uow);
    }
    
    public  MethodOutcome writeMediaIntoDM(Media media){
        String mediaString = convertToJSONString(media);
        MethodOutcome outcome = writeMediaIntoDM(mediaString);
        return(outcome);
    }
        
    public  MethodOutcome writeMediaIntoDM(String mediaAsString){
        getLogger().debug(".utiliseMediaPersistenceCapability(): Entry, mediaAsString --> {}", mediaAsString);
        //
        // Build Write
        //
        CapabilityUtilisationRequest task = new CapabilityUtilisationRequest();
        task.setRequestID(UUID.randomUUID().toString());
        task.setRequestContent(mediaAsString);
        task.setRequiredCapabilityName("FHIR-Media-Persistence");
        task.setRequestInstant(Instant.now());
        //
        // Do Write
        //
        CapabilityUtilisationResponse mediaWriteOutcome = capabilityUtilisationBroker.executeTask(MEDIA_PERSISTENCE_DATA_MANAGER, task);
        //
        // Extract the response
        //
        String resultString = mediaWriteOutcome.getResponseStringContent();
        MethodOutcome methodOutcome = convertToMethodOutcome(resultString);
        getLogger().debug(".utiliseMediaPersistenceCapability(): Entry, methodOutcome --> {}", methodOutcome);
        return(methodOutcome);
    }
    
    private String convertToJSONString(Media media){
        String mediaString = fhirParser.encodeResourceToString(media);
        return(mediaString);
    }
    
    private MethodOutcome convertToMethodOutcome(String methodOutcomeString){
        if(StringUtils.isEmpty(methodOutcomeString)){
            MethodOutcome outcome = new MethodOutcome();
            outcome.setCreated(false);
            return(outcome);
        }
        PegacornTransactionOutcome transactionOutcome = null;
        try {
            transactionOutcome = getJSONMapper().readValue(methodOutcomeString, PegacornTransactionOutcome.class);
        } catch (JsonProcessingException e) {
            getLogger().error(".convertToMethodOutcome(): Cannot parse MethodOutcome object! ", e);
        }
        MethodOutcome methodOutcome = null;
        if(transactionOutcome != null){
            String resourceURL = null;
            String resourceType = "Media";
            if(transactionOutcome.isTransactionSuccessful()) {
                String resourceValue = transactionOutcome.getResourceID().getValue();
                String resourceVersion = SimpleResourceID.DEFAULT_VERSION;
                if(transactionOutcome.getResourceID() != null) {
                    if (transactionOutcome.getResourceID().getResourceType() != null) {
                        resourceType = transactionOutcome.getResourceID().getResourceType();
                    }
                    if (transactionOutcome.getResourceID().getVersion() != null) {
                        resourceVersion = transactionOutcome.getResourceID().getVersion();
                    }
                    if (transactionOutcome.getResourceID().getUrl() != null) {
                        resourceURL = transactionOutcome.getResourceID().getUrl();
                    }
                    IdType id = new IdType();
                    id.setParts(resourceURL, resourceType, resourceValue, resourceVersion);
                    methodOutcome = new MethodOutcome();
                    methodOutcome.setId(id);
                    methodOutcome.setCreated(transactionOutcome.isTransactionSuccessful());
                }
            }
        }
        if(methodOutcome == null) {
            methodOutcome = new MethodOutcome();
            methodOutcome.setCreated(false);
        }
        return(methodOutcome);
    }


    
    //
    // Getters (and Setters)
    //
    
    protected Logger getLogger() {
        return(LOG);
    }

    public boolean isInitialised() {
        return initialised;
    }

    protected void setInitialised(boolean initialisationStatus){
        this.initialised = initialisationStatus;
    }
    
    protected ProcessingPlantInterface getProcessingPlant(){
        return(processingPlant);
    }
    
    protected ObjectMapper getJSONMapper(){
        return(this.jsonMapper);
    }
    
}
