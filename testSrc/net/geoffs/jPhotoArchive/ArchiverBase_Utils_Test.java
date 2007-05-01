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
        assertHasNumber(1, "Photo File (jPA-1).jpg");
        assertHasNumber(3, "Photo File (jPA-3).jpg");
        assertHasNumber(45, "Photo File (jPA-45).jpg");
        assertHasNumber(123456, "Photo File (jPA-123456).jpg");
        
        assertHasNumber(123, "Photo File (test) (jPA-123).jpg");
        
        // Really pathological cases:
        assertHasNumber(543, "Photo File (jPA-1) (jPA-543).jpg");
        assertHasNumber(1, "Photo File (jPA-5) (jPA-1).jpg");
    }

    public void testHasPreviousVersionNumber_false()
    {
        assertHasNoNumber("Photo File.jpg");
        assertHasNoNumber("Photo File (1).jpg");
        assertHasNoNumber("Photo File (blah-1).jpg");
        assertHasNoNumber("Photo File (JPA-1).jpg");
        assertHasNoNumber("Photo File (jpa-1).jpg");
        assertHasNoNumber("Photo File jPa-1.jpg");
        assertHasNoNumber("Photo File [jPa-1].jpg");
        assertHasNoNumber("Photo File {jPa-1}.jpg");
        assertHasNoNumber("Photo File (jPa-1.jpg");
        assertHasNoNumber("Photo File jPa-1).jpg");
        
        assertHasNoNumber("Photo File.jpg (jPA-1)");
        assertHasNoNumber("Photo File(jPA-1).jpg");   // no space before "("
        assertHasNoNumber("Photo File (jPA-1) .jpg"); // space before ".jpg"
        
    }
    
    private void assertHasNoNumber(String fileWithNoNumber)
    {
        assertHasNumberStatus(fileWithNoNumber, false);
        assertNumber(-1, fileWithNoNumber);
    }

    private void assertHasNumber(int expectedNumber, String fileWithANumber)
    {
        assertHasNumberStatus(fileWithANumber, true);
        assertNumber(expectedNumber, fileWithANumber);
    }
    
    private void assertHasNumberStatus(String filename, boolean expectedStatus)
    {
        assertEquals("Wrong result for: "+filename, expectedStatus, 
                ArchiverBase.hasPreviousVersionNumber(filename));
    }

    private void assertNumber(int expectedNumber, String filename)
    {
        assertEquals(expectedNumber, ArchiverBase.getVersionNumber(filename));
    }
}
