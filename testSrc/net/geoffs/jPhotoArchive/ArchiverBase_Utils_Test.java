package net.geoffs.jPhotoArchive;

import junit.framework.TestCase;

public class ArchiverBase_Utils_Test extends TestCase
{
    public void testGetArgOrNull()
    {
        String[] testArgs = new String[]{"arg1", "arg2", "arg3"};
        
        assertEquals("arg1", Main.getArgOrNull(testArgs, 0));
        assertEquals("arg2", Main.getArgOrNull(testArgs, 1));
        assertEquals("arg3", Main.getArgOrNull(testArgs, 2));
        assertNull(          Main.getArgOrNull(testArgs, 3));
        assertNull(          Main.getArgOrNull(testArgs, 10));
        assertNull(          Main.getArgOrNull(testArgs, 1000));
    }
    
    public void testHasPreviousVersionNumber_true()
    {
        String fileWithANumber = "Photo File (jPA-3).jpg";
        assertEquals("Wrong result for: "+fileWithANumber, true, ArchiverBase.hasPreviousVersionNumber(fileWithANumber));
    }
    
    public void testHasPreviousVersionNumber_false()
    {
        String fileWithNone = "Photo File (blah-1).jpg";
        assertEquals("Wrong result for: "+fileWithNone, false, ArchiverBase.hasPreviousVersionNumber(fileWithNone));
    }
}
