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
package net.fhirfactory.pegacorn.hestia.dam.im.workshops.internalipc.petasos.services;

import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.hl7.fhir.r4.model.Media;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

import ca.uhn.fhir.rest.api.MethodOutcome;
import net.fhirfactory.pegacorn.core.interfaces.media.PetasosMediaServiceAgentInterface;
import net.fhirfactory.pegacorn.core.interfaces.media.PetasosMediaServiceBrokerInterface;
import net.fhirfactory.pegacorn.core.interfaces.media.PetasosMediaServiceClientWriterInterface;
import net.fhirfactory.pegacorn.core.interfaces.topology.ProcessingPlantInterface;
import net.fhirfactory.pegacorn.core.interfaces.topology.ProcessingPlantRoleSupportInterface;
import net.fhirfactory.pegacorn.hestia.dam.im.workshops.datagrid.AsynchronousWriterMediaCache;
import net.fhirfactory.pegacorn.hestia.dam.im.workshops.internalipc.ask.beans.HestiaDMHTTPClient;

@ApplicationScoped
public class PetasosMediaPersistenceService implements PetasosMediaServiceClientWriterInterface,
        PetasosMediaServiceBrokerInterface, PetasosMediaServiceAgentInterface {
    private static final Logger LOG = LoggerFactory.getLogger(PetasosMediaPersistenceService.class);

    private ObjectMapper jsonMapper;

    private boolean stillRunning;
    private Object writerLock;

    private Long ASYNC_MEDIA_WRITER_STARTUP_DELAY = 60000L;
    private Long ASYNC_MEDIA_WRITER_CHECK_PERIOD = 10000L;

    @Inject
    private ProcessingPlantInterface processingPlant;

    @Inject
    private HestiaDMHTTPClient hestiaDMHTTPClient;

    @Inject
    private AsynchronousWriterMediaCache mediaCache;

    //
    // Constructor(s)
    //

    public PetasosMediaPersistenceService() {
        jsonMapper = new ObjectMapper();
        stillRunning = false;
        writerLock = new Object();
        scheduleAsynchronousMediaWriterDaemon();
    }

    //
    // Getters (and Setters)
    //

    protected Logger getLogger() {
        return (LOG);
    }

    protected ProcessingPlantInterface getProcessingPlant() {
        return (this.processingPlant);
    }

    protected ObjectMapper getJSONMapper() {
        return (jsonMapper);
    }

    protected HestiaDMHTTPClient getHestiaDMHTTPClient() {
        return (hestiaDMHTTPClient);
    }

    protected AsynchronousWriterMediaCache getMediaCache(){
        return(mediaCache);
    }

    protected Object getWriterLock(){
        return(writerLock);
    }

    //
    // Global Media Services
    //


    @Override
    public MethodOutcome writeMediaJSONStringSynchronously(String mediaJSONString) {
        getLogger().debug(".writeMediaJSONStringSynchronously(): Entry, media->{}", mediaJSONString);
        MethodOutcome methodOutcome;

        synchronized (getWriterLock()) {
            methodOutcome = getHestiaDMHTTPClient().writeMedia(mediaJSONString);
        }
        getLogger().debug(".writeMediaJSONStringSynchronously(): Exit, methodOutcome->{}", methodOutcome);
        return(methodOutcome);
    }

    @Override
    public MethodOutcome writeMediaAsynchronously(Media media) {
        getLogger().debug(".writeMediaAsynchronously(): Entry, media->{}", media);
        MethodOutcome outcome = writeMedia(media);
        getLogger().debug(".writeMediaAsynchronously(): Exit, outcome->{}", outcome);
        return(outcome);
    }

    @Override
    public MethodOutcome writeMediaSynchronously(Media media) {
        getLogger().debug(".writeMediaSynchronously(): Entry, media->{}", media);
        MethodOutcome outcome = writeMedia(media);
        getLogger().debug(".writeMediaSynchronously(): Exit, outcome->{}", outcome);
        return(outcome);
    }

    //
    // Actual Writing Invocation Function
    //

    public MethodOutcome writeMedia(Media media) {
        getLogger().debug(".writeMedia(): Entry, media->{}", media);
        MethodOutcome outcome = null;
        if(media != null) {
            getLogger().debug(".writeMedia(): Media is not -null-, writing!");
            synchronized (getWriterLock()) {
                getLogger().debug(".writeMedia(): Got Writing Semaphore, writing!");
                outcome = getHestiaDMHTTPClient().writeMedia(media);
            }
        }
        getLogger().debug(".writeMedia(): Exit, media->{}", media);
        return (outcome);
    }

    @VisibleForTesting
    SecretKey createSecretKey() {
    	try {
			return KeyGenerator.getInstance("AES").generateKey();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
    	return null;
    }
    @VisibleForTesting
    String generateFilename(Media media) {
    	Calendar c = Calendar.getInstance();
    	StringBuilder sb = new StringBuilder();
    	//Set the folder structure based on
    	sb.append("/");
    	sb.append(c.get(Calendar.YEAR));
    	sb.append("/");
    	sb.append(c.get(Calendar.MONTH) + 1);
    	sb.append("/");
    	sb.append(c.get(Calendar.DATE));
    	sb.append("/");
    	sb.append(media.getId());  	//TODO KS work out which parts of the algorithm need to be saved

    	return sb.toString();
    }

    //
    // Local Media Broker Services
    //
    @Override
    public Boolean logMedia(String serviceProviderName, Media media) {
        getLogger().debug(".logMedia(): Entry, media->{}", media);
        MethodOutcome outcome = writeMedia(media);
        Boolean success = false;
        if(outcome != null){
            success = outcome.getCreated();
        }
        getLogger().debug(".logMedia(): Exit, success->{}", success);
        return(success);
    }

    @Override
    public Boolean logMedia(String serviceProviderName, List<Media> mediaList) {
        getLogger().debug(".logMedia(): Entry, media->{}", mediaList);
        Boolean success = false;
        if(mediaList != null){
            if(!mediaList.isEmpty()){
                for(Media currentMedia: mediaList){
                    getMediaCache().addMedia(currentMedia);
                }
            }
        }
        success = true;
        getLogger().debug(".logMedia(): Exit, success->{}", success);
        return(success);
    }

    //
    // Helper Functions
    //


    //
    // Asynchronous Writer Daemon
    //

    //
    // Scheduler

    private void scheduleAsynchronousMediaWriterDaemon() {
        getLogger().debug(".scheduleAsynchronousMediaWriterDaemon(): Entry");
        TimerTask asynchronousMediaWriterDaemon = new TimerTask() {
            public void run() {
                getLogger().debug(".asynchronousMediaWriterDaemon(): Entry");
                asynchronousMediaWriterTask();
                getLogger().debug(".asynchronousMediaWriterDaemon(): Exit");
            }
        };
        Timer timer = new Timer("AsynchronousMediaTimer");
        timer.schedule(asynchronousMediaWriterDaemon, ASYNC_MEDIA_WRITER_STARTUP_DELAY, ASYNC_MEDIA_WRITER_CHECK_PERIOD);
        getLogger().debug(".scheduleAsynchronousMediaWriterDaemon(): Exit");
    }

    //
    // Task

    private void asynchronousMediaWriterTask(){
        getLogger().debug(".notificationForwarder(): Entry");
        stillRunning = true;

        while(getMediaCache().hasEntries()) {
            getLogger().trace(".notificationForwarder(): Entry");
            Media currentMedia = getMediaCache().peekMedia();
            MethodOutcome outcome = null;
            synchronized (getWriterLock()) {
                outcome = hestiaDMHTTPClient.writeMedia(currentMedia);
            }
            boolean success = false;
            if(outcome != null) {
                if (outcome.getCreated()) {
                    getMediaCache().pollMedia();
                    success = true;
                }
            }
            if(!success){
                getLogger().warn(".asynchronousMediaWriterTask(): Failed to write Media!");
                break;
            }
        }
        stillRunning = false;
        getLogger().debug(".notificationForwarder(): Exit");
    }

    //
    // Media Client Service
    //


    @Override
    public Boolean captureMedia(Media media, boolean synchronous) {
        Boolean success = logMedia(getProcessingPlant().getSubsystemParticipantName(), media);
        return(success);
    }

}
