package net.fhirfactory.pegacorn.hestia.dam.im.cipher;

import javax.crypto.SecretKey;

import ca.uhn.fhir.rest.api.MethodOutcome;

public interface EncryptedByteArrayStorage {

	MethodOutcome encryptAndSave(SecretKey secretKey, String fileName, byte[] content);

	byte[] loadAndDecrypt(SecretKey key, String fileName);

}
