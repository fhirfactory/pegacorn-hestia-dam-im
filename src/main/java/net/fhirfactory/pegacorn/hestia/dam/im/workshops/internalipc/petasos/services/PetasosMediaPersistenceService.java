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

import org.hl7.fhir.r4.model.Attachment;
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
import net.fhirfactory.pegacorn.hestia.dam.im.cipher.EncryptedByteArrayStorage;
import net.fhirfactory.pegacorn.hestia.dam.im.cipher.FileEncrypterDecrypter;
import net.fhirfactory.pegacorn.hestia.dam.im.workshops.datagrid.AsynchronousWriterMediaCache;
import net.fhirfactory.pegacorn.hestia.dam.im.workshops.internalipc.ask.beans.HestiaDMHTTPClient;
import net.fhirfactory.pegacorn.internals.fhir.r4.resources.media.factories.MediaEncryptionExtensionFactory;

@ApplicationScoped
public class PetasosMediaPersistenceService implements PetasosMediaServiceClientWriterInterface {
    private static final Logger LOG = LoggerFactory.getLogger(PetasosMediaPersistenceService.class);

    private ObjectMapper jsonMapper;
    private Object writerLock;

    @Inject
    private ProcessingPlantInterface processingPlant;

    @Inject
    private HestiaDMHTTPClient hestiaDMHTTPClient;

    @Inject
    private AsynchronousWriterMediaCache mediaCache;
    
    @Inject
    MediaEncryptionExtensionFactory encryptionExtension;
    
    @Inject
    EncryptedByteArrayStorage storageInterface;

    //
    // Constructor(s)
    //

    public PetasosMediaPersistenceService() {
        jsonMapper = new ObjectMapper();
        writerLock = new Object();
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
        //XXX KS if media has URL to begin with, should it at least be saved?
        if(media != null && media.getContent() != null && media.getContent().getData() != null) {
          //1. generate filename
            String filename = generateFilename(media);
          //2. generate secret key
            SecretKey key = createSecretKey();
          //3. call media persistence
            //4. if outcome = true
            outcome = storageInterface.encryptAndSave(key, filename, media.getContent().getData());
            if(outcome.getCreated()) {
            //   a) update media to have the file URL and secretKey
            	Attachment a = media.getContent();
            	a.setUrl(filename);
            	a.setData(null);
            	encryptionExtension.injectSecretKey(a, key);

            //	 b) write the modified media to the JPA server
	            synchronized (getWriterLock()) {
//	                getLogger().warn(".writeMedia(): Got Writing Semaphore, writing!");
	                outcome = getHestiaDMHTTPClient().writeMedia(media);
	            }
            }
        } else {
            getLogger().warn(".writeMedia(): Media isn't properly formed, not writing!");
        }
        getLogger().debug(".writeMedia(): Exit, media->{}", media);
        return (outcome);
    }
    

	public Media readMedia(Media media) {
		getLogger().debug(".readMedia() entry media->{}", media);
		if(media == null || media.getContent() == null || !media.getContent().hasExtension()) {
			getLogger().warn("readMedia() failed to read media because of insufficient data passed to method. media->{}", media);
		}
		SecretKey key = encryptionExtension.extractSecretKey(media.getContent());
		String fileName = media.getContent().getUrl();
		getLogger().debug("attempting to retrieveObject with the following: key ->{}, fileName ->{}", key.getEncoded(), fileName);
		byte[] data = storageInterface.loadAndDecrypt(key, fileName);
		if(data == null) {
			getLogger().warn(".readMedia() failed to retrieve data.");
		} else {
			media.getContent().setData(data);
		}
		getLogger().debug(".readMedia() exit.");

		return media;
	}

    //
    // Helper Functions
    //
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
    	//Set the folder structure based on year month and day
    	//XXX KS change this to use the created date on the media object maybe?
    	sb.append("/");
    	sb.append(c.get(Calendar.YEAR));
    	sb.append("/");
    	sb.append(c.get(Calendar.MONTH) + 1);
    	sb.append("/");
    	sb.append(c.get(Calendar.DATE));
    	sb.append("/");
    	sb.append(media.getId());  	//XXX KS reconsider file name
    	sb.append(".data"); 		//XXX KS reconsider extension
    	return sb.toString();
    }
}
