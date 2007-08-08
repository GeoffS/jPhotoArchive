package net.geoffs.jPhotoArchive;

import junit.framework.TestCase;

public class FilenameFixerTest extends TestCase
{
    static final String ORI_TEST_NAME = "test name.jpg";
    static final String V1_TEST_NAME  = "test name (jPA-1).jpg";
    static final String V2_TEST_NAME  = "test name (jPA-2).jpg";
    static final String V3_TEST_NAME  = "test name (jPA-3).jpg";
    static final String V4_TEST_NAME  = "test name (jPA-4).jpg";
    static final String V5_TEST_NAME  = "test name (jPA-5).jpg";
    
    public void testNoRename()
    {
        String[] existingFiles = new String[] {"a.jpg", "b.jog", "c.jpg"};
        
        String newName = ArchiverBase.makeVersionedName(ORI_TEST_NAME, existingFiles);
        
        // makeVersionedName() *always* makes a non-existing versioned name
        // (i.e. with the "(jPA-#)" string inserted, even if the original name
        // doesen't exist.  It's up to the caller to check uniqueness if they care.
        assertEquals(V1_TEST_NAME, newName);
    }
    
    public void testFirstRename()
    {
        String[] existingFiles = new String[] {"a.jpg", ORI_TEST_NAME, "c.jpg"};
        
        String newName = ArchiverBase.makeVersionedName(ORI_TEST_NAME, existingFiles);
        
        assertEquals(V1_TEST_NAME, newName);
    }
    
    public void testSecondRename()
    {
        String[] existingFiles = new String[] {"a.jpg", ORI_TEST_NAME, "c.jpg", V1_TEST_NAME};
        
        String newName = ArchiverBase.makeVersionedName(ORI_TEST_NAME, existingFiles);
        
        assertEquals(V2_TEST_NAME, newName);
    }
    
    public void testManyRename()
    {
        String[] existingFiles = new String[] {V4_TEST_NAME, 
                                               V2_TEST_NAME, 
                                               V3_TEST_NAME, 
                                               "a.jpg", 
                                               ORI_TEST_NAME, 
                                               "c.jpg", 
                                               V1_TEST_NAME};
        
        String newName = ArchiverBase.makeVersionedName(ORI_TEST_NAME, existingFiles);
        
        assertEquals(V5_TEST_NAME, newName);
    }
    
//    private static class MockDir extends File
//    {
//        private static final long serialVersionUID = 1L;
//        private final Set<String> entries;
//        
//        public MockDir(String[] testEntries)
//        {
//            super("");
//            
//            entries = new HashSet<String>();
//            for (int i = 0; i < testEntries.length; i++)
//            {
//                entries.add(testEntries[i]);
//            }
//        }
//        
//    }
}
