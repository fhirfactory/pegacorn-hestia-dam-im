package net.fhirfactory.pegacorn.hestia.dam.im.workshops.internalipc.petasos.services;

import static org.junit.jupiter.api.Assertions.*;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.hl7.fhir.r4.model.Media;

class PetasosMediaPersistenceServiceTest {

	@Test
	void generateFilename() {
		PetasosMediaPersistenceService service = new PetasosMediaPersistenceService();
		Media media = getDefaultMedia();
		String path = service.generateFilename(media);
		Assertions.assertNotNull(path);
		Assertions.assertNotEquals("", path);	
		System.out.println(path);
	}
	


	@Test
	void generateSecretKey() {
		PetasosMediaPersistenceService service = new PetasosMediaPersistenceService();
		SecretKey key = service.createSecretKey();
		Assertions.assertNotNull(key);
		System.out.println(key);
		
	}

	//TODO KS - flesh out builder with default values
	private Media getDefaultMedia() {
		// TODO Auto-generated method stub
		Media media = new Media();
		String id = "123id";
		media.setId(id);
		return media;
	}
}
