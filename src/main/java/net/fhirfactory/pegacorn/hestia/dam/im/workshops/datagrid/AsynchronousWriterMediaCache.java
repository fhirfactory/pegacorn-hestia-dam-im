package net.fhirfactory.pegacorn.hestia.dam.im.workshops.datagrid;

import org.hl7.fhir.r4.model.Media;

import javax.enterprise.context.ApplicationScoped;
import java.util.concurrent.ConcurrentLinkedQueue;

@ApplicationScoped
public class AsynchronousWriterMediaCache {

    private ConcurrentLinkedQueue<Media> mediaQueue;

    //
    // Constructor
    //

    public AsynchronousWriterMediaCache(){
        this.mediaQueue = new ConcurrentLinkedQueue<>();
    }

    //
    // Getters (and Setters)
    //


    public ConcurrentLinkedQueue<Media> getMediaQueue() {
        return mediaQueue;
    }

    //
    // Basic Methods
    //

    public void addMedia(Media media){
        getMediaQueue().offer(media);
    }

    public Media peekMedia(){
        Media nextMedia = getMediaQueue().peek();
        return(nextMedia);
    }

    public Media pollMedia(){
        Media nextMedia = getMediaQueue().poll();
        return(nextMedia);
    }

    public boolean hasEntries(){
        boolean hasAtLeastOneEntry = !(getMediaQueue().isEmpty());
        return(hasAtLeastOneEntry);
    }
}
