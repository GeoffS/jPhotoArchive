package net.geoffs.jPhotoArchive;

public class Locks
{
    // There is no need for a lock on the data-base.
    // All the methods are synchronized.
    
    public static final Object SRC_DEVICE_ACCESS = new Locks("SRC_DEVICE_ACCESS");
    public static final Object DST_DEVICE_ACCESS = new Locks("DST_DEVICE_ACCESS");
    
    private final String id;
    
    private Locks(final String id)
    {
        this.id = id;
    }
    
    public String toString(){ return id; }
}
