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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Media;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;

import ca.uhn.fhir.rest.api.MethodOutcome;
import net.fhirfactory.pegacorn.core.interfaces.media.PetasosMediaServiceAgentInterface;
import net.fhirfactory.pegacorn.core.interfaces.media.PetasosMediaServiceClientWriterInterface;
import net.fhirfactory.pegacorn.core.interfaces.topology.ProcessingPlantInterface;
import net.fhirfactory.pegacorn.hestia.dam.im.cipher.EncryptedByteArrayStorage;
import net.fhirfactory.pegacorn.hestia.dam.im.workshops.internalipc.ask.beans.HestiaDMHTTPClient;
import net.fhirfactory.pegacorn.internals.fhir.r4.resources.media.factories.MediaEncryptionExtensionException;
import net.fhirfactory.pegacorn.internals.fhir.r4.resources.media.factories.MediaEncryptionExtensionFactory;
import net.fhirfactory.pegacorn.util.FHIRContextUtility;

@ApplicationScoped
public class PetasosMediaPersistenceService implements PetasosMediaServiceClientWriterInterface, PetasosMediaServiceAgentInterface {
    private static final Logger LOG = LoggerFactory.getLogger(PetasosMediaPersistenceService.class);

    private ObjectMapper jsonMapper;
    private Object writerLock;

    @Inject
    private ProcessingPlantInterface processingPlant;

    @Inject
    private HestiaDMHTTPClient hestiaDMHTTPClient;
    
    @Inject
    MediaEncryptionExtensionFactory encryptionExtension;
    
    @Inject
    EncryptedByteArrayStorage storageInterface;
    
    @Inject
    FHIRContextUtility contextUtility;

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


    protected Object getWriterLock(){
        return(writerLock);
    }

    //
    // Global Media Services
    //

   

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

            	LOG.trace("Media before save ->{}", contextUtility.getJsonParser().encodeResourceToString(media));
            //	 b) write the modified media to the JPA server
	            synchronized (getWriterLock()) {
	                getLogger().debug(".writeMedia(): Got Writing Semaphore, writing!");
	                outcome = getHestiaDMHTTPClient().writeMedia(media);
	            }
	            if(!outcome.getCreated()) {
                    getLogger().warn(".writeMedia(): Failed to save Media to DB!");
                } else {
                    media = (Media) outcome.getResource();  // the only change here should just be the ID
                    getLogger().trace("Media after save ->{}", contextUtility.getJsonParser().encodeResourceToString(media));
                }
            }
        } else {
            getLogger().warn(".writeMedia(): Media isn't properly formed, not writing!");
        }
        getLogger().debug(".writeMedia(): Exit, media->{}", media.toString());
        return (outcome);
    }
    

	public Media readMedia(Media media) throws MediaPersistenceException {
		getLogger().debug(".readMedia(): Entry media->{}", media);
		if (media == null) {
		    throw new IllegalArgumentException("Cannot read media: no media provided");
		}
		if (media.getContent() == null) {
		    throw new MediaPersistenceException("Cannot read media: no content.  media->" + media);
		}
		if (!media.getContent().hasExtension()) {
		    throw new MediaPersistenceException("Cannot read media: no content extension.  media->" + media);
		}
		SecretKey key;
        try {
            key = encryptionExtension.extractSecretKey(media.getContent());
        } catch (MediaEncryptionExtensionException e) {
            throw new MediaPersistenceException("Cannot read media: cannot extract secret key.  media->" + media, e);
        }
		String fileName = media.getContent().getUrl();
		if (fileName == null) {
		    throw new MediaPersistenceException("Cannot read media: no content URL (filename).  media->" + media);
		}
		getLogger().debug("attempting to retrieveObject with the following: key ->{}, fileName ->{}", key.getEncoded(), fileName);
		byte[] data;
        try {
            data = storageInterface.loadAndDecrypt(key, fileName);
        } catch (GeneralSecurityException | IOException e) {
            throw new MediaPersistenceException("Failed to load and decrypt media", e);
        }
		if (data == null) {
		    throw new MediaPersistenceException("Failed to load and decrypt media: null return");
		}
		media.getContent().setData(data);

		getLogger().debug(".readMedia(): Exit");
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
    	sb.append("/");
    	sb.append(c.get(Calendar.YEAR));
    	sb.append("/");
    	sb.append(c.get(Calendar.MONTH) + 1);
    	sb.append("/");
    	sb.append(c.get(Calendar.DATE));
    	sb.append("/");
    	sb.append(media.getId());
    	sb.append(".data"); 		//XXX KS reconsider extension
    	return sb.toString();
    }

	@Override
	public Boolean captureMedia(Media media) {
        getLogger().debug(".captureMedia(): Entry, captureMedia->{}", media);
        MethodOutcome outcome = writeMedia(media);
        Boolean success = false;
        if(outcome != null){
            success = outcome.getCreated();
        }
        getLogger().debug(".captureMedia(): Exit, success->{}", success);
        return(success);
	}

	@Override
	public Media loadMedia(String mediaId) {
		getLogger().debug(".loadMedia() entry mediaId->{}", mediaId);

		if(StringUtils.isEmpty(mediaId)) {
			return (null);
		}
		//Load media object from JPA server
		Media media = getHestiaDMHTTPClient().readMedia(mediaId);
		//
		getLogger().debug(".loadMedia() exit media->{}", media);
		try {
            return readMedia(media);
        } catch (MediaPersistenceException e) {
            getLogger().error("Failed to load media: mediaId->" + mediaId, e);
            return null;
        }
	}
}
