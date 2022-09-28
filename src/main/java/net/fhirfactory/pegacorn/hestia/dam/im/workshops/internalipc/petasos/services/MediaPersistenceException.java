package net.fhirfactory.pegacorn.hestia.dam.im.workshops.internalipc.petasos.services;

public class MediaPersistenceException extends Exception {
    private static final long serialVersionUID = 1L;

    public MediaPersistenceException(String message) {
        super(message);
    }
    
    public MediaPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
