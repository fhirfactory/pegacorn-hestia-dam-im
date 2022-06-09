package net.fhirfactory.pegacorn.hestia.dam.im.workshops.internalipc.petasos.services;

import static org.junit.jupiter.api.Assertions.*;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.ibm.fhir.model.resource.Media;

class PetasosMediaPersistenceServiceTest {

	@Test
	void generateFilename() {
		PetasosMediaPersistenceService service = new PetasosMediaPersistenceService();
		Media media = getDefaultMedia();
		String path = service.generateFilename(null);
		Assertions.assertNotNull(path);
		Assertions.assertNotEquals("", path);	
	}
	
	@Test
	void generateSecretKey() {
		PetasosMediaPersistenceService service = new PetasosMediaPersistenceService();
		SecretKey key = service.createSecretKey();
		Assertions.assertNotNull(key);
		
	}

	//TODO KS - flesh out builder with default values
	private Media getDefaultMedia() {
		Media media = Media.builder().build();		
		return media;
	}
}
