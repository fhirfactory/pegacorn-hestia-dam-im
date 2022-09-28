package net.fhirfactory.pegacorn.hestia.dam.im.workshops.internalipc.petasos.services;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

//import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Media;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.rest.api.MethodOutcome;

/*
 * A "test" class to generate Media objects 
 * Used to test the save / load functionality inside a docker container
 */
//@ApplicationScoped
public class MediaObjectGenerator {
	
    private static final Logger LOG = LoggerFactory.getLogger(MediaObjectGenerator.class);
    int count = 0;
    int maxCount = 0; //Set higher 

	@Inject
	private PetasosMediaPersistenceService service;
	
	private boolean save = true;
	
	private Media temp;

	public MediaObjectGenerator() {
		getLogger().debug("MediaObjectGenerator() - entry");
	 TimerTask MediaGeneratorTask = new TimerTask() {
         public void run() {
             getLogger().debug(".MediaObjectGenerator.run(): Entry");
             storeAndRetrieve();
             getLogger().debug(".MediaObjectGenerator.run(): Exit");
         }
     };
     Timer t = new Timer();
     t.scheduleAtFixedRate(MediaGeneratorTask, 20000, 20000);
		getLogger().debug("MediaObjectGenerator() - exit");
	}
	
	public void storeAndRetrieve() {
		getLogger().debug(".storeAndRetrieve() entry: save->{}", save);
		if(count < maxCount) {
			if(save) {
				if(temp == null) {
					temp = generateMediaObject();
				}
				getLogger().debug("New object generated: " + temp);
				if(service != null) {
					MethodOutcome outcome = service.writeMedia(temp);
					if(outcome.getCreated()) {
						save = false;
					} else {
						temp = null;
					}
				} else {
					getLogger().debug("service didn't set correctly" + (service == null));
				}
			} else {
				getLogger().debug("service is null? " + (service == null));
				if(service != null) {
				    Media media = null;
				    try {
				        media = service.readMedia(temp);
				    } catch (MediaPersistenceException e) {
				        getLogger().error(".storeAndRetrieve(): Exit, Failed reading media " + temp, e);
				    }
				    if (media != null) {
    					getLogger().debug("data returned: ->{}",media.getContent().getData());
    					getLogger().debug("data equal? ->{}", media.getContent().getData() == temp.getContent().getData());
    					save = true;
    					temp = null;
				    }
				} else {
					getLogger().debug("service didn't set correctly" + (service == null));
				}
			}
			++count;
		}
		getLogger().debug(".storeAndRetrieve() exit");
	}
	
	private Media generateMediaObject() {
		Media media = new Media();
		media.setId("" + new Random().nextInt());
		Attachment attachment = new Attachment();
		byte[] data = getSampleData();
		attachment.setData(data);
		media.setContent(attachment );
		//populate as needed. Currently sufficient for the purposes.
		return media;
	}
	
	//Call to trigger the class
	public void startTimer() {
		getLogger().debug(".startTimer() called");
		//Nothing to do
	}

    protected Logger getLogger() {
        return (LOG);
    }
    
    //Base64 encoded GIF
    private byte[] getSampleData() {
    	Base64 base64 = new Base64();
    	String data  = "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";
    	return base64.decode(data);
    }

}
