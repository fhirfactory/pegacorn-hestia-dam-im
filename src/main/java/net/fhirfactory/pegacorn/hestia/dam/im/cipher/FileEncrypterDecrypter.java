package net.fhirfactory.pegacorn.hestia.dam.im.cipher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.rest.api.MethodOutcome;


//TODO remove this class from IM and wire up IM to DM in yaml
@ApplicationScoped
public class FileEncrypterDecrypter implements EncryptedByteArrayStorage {
	
	private static String CIPHER_KEY = "AES/CBC/PKCS5Padding";
    private static final Logger LOG = LoggerFactory.getLogger(FileEncrypterDecrypter.class);
	
    private Cipher cipher;
    
    private String prefix;
    
    public FileEncrypterDecrypter() {
    	this(CIPHER_KEY);
    }
    
    protected Logger getLogger() {
        return (LOG);
    }
    
    public FileEncrypterDecrypter(String cipherKey) {
    	try {
			cipher = Cipher.getInstance(cipherKey);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		}
    	prefix = (System.getenv("STORAGE_PATH"));
    }

    //For unit testing purposes only
	void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	@Override
	public MethodOutcome encryptAndSave(SecretKey secretKey, String fileName, byte[] content) {
		getLogger().debug(".encryptAndSave() entry: fileName ->{} content ->{}", fileName, content);
		
		MethodOutcome outcome = null;
		try {
			cipher.init(Cipher.ENCRYPT_MODE, secretKey);

			byte[] iv = cipher.getIV();			
			
			File file = new File(prefix+fileName);
			file.getParentFile().mkdirs();
			if(!file.exists()) {
				FileOutputStream fileOut = new FileOutputStream(file);
				CipherOutputStream cipherOut = new CipherOutputStream(fileOut, cipher);
				fileOut.write(iv);
				cipherOut.write(content);
				outcome = new MethodOutcome();
				outcome.setCreated(true);
				cipherOut.close();
			} else {
				getLogger().warn("File not unique! ->{}, ", fileName);
				outcome = new MethodOutcome();
				outcome.setCreated(false);
			}
			getLogger().debug(".encryptAndSave() fileName -> {} created successfully", fileName);
		} catch (Exception ex) {
			getLogger().error(".encryptAndSave(): ", ex);
			outcome = new MethodOutcome();
			outcome.setCreated(false);
		}
		getLogger().debug(".encryptAndSave() exit: outcome.getCreated -> {}", outcome.getCreated());

		return outcome;
	}
	
	@Override
	public byte[] loadAndDecrypt(SecretKey key, String fileName) throws GeneralSecurityException, IOException {
		getLogger().debug(".loadAndDecrypt() key ->{} fileName -> ", key.getEncoded(), fileName);
		byte[] content;

        try (FileInputStream fileIn = new FileInputStream(prefix + fileName)) {
            byte[] fileIv = new byte[16];
            fileIn.read(fileIv);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(fileIv));

            try (CipherInputStream cipherIn = new CipherInputStream(fileIn, cipher)) {
            	content = cipherIn.readAllBytes();
            }

            return content;
        }
	}
}
