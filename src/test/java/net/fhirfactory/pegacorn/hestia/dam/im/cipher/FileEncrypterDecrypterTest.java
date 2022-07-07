package net.fhirfactory.pegacorn.hestia.dam.im.cipher;

import java.io.File;
import java.security.NoSuchAlgorithmException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.rest.api.MethodOutcome;

class FileEncrypterDecrypterTest {

	@Test
	void testSave() {
		FileEncrypterDecrypter fed = new FileEncrypterDecrypter();
		fed.setPrefix("./");
		String filename = "filename.enc";
		try {
			SecretKey secretKey = KeyGenerator.getInstance("AES").generateKey();

			byte[] content = "content123".getBytes();

			MethodOutcome outcome = fed.encryptAndSave(secretKey, filename, content);
			Assertions.assertTrue(outcome.getCreated());
			
			byte[] returnedContent = fed.loadAndDecrypt(secretKey, filename);
			Assertions.assertEquals(content.length, returnedContent.length);
			for(int i = 0; i < content.length; i++) {
				Assertions.assertEquals(content[i], returnedContent[i]);
			}

		} catch (NoSuchAlgorithmException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			Assertions.fail(e1);
		} finally {
			new File("./" + filename).delete();
		}

	}
	
	

}
