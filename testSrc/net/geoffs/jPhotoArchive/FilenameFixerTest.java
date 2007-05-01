package net.geoffs.jPhotoArchive;

import junit.framework.TestCase;

public class FilenameFixerTest extends TestCase
{
    public void testNoRename()
    {
        String startingName = "test name.jpg";
        String[] existingFiles = new String[] {"a.jpg", "b.jog", "c.jpg"};
        
        String newName = ArchiverBase.makeVersionedName(startingName, existingFiles);
        
        assertEquals(startingName, newName);
    }
    
    public void testFirstRename()
    {
        String startingName = "test name.jpg";
        String[] existingFiles = new String[] {"a.jpg", "test name.jpg", "c.jpg"};
        
        String newName = ArchiverBase.makeVersionedName(startingName, existingFiles);
        
        assertEquals("test name (jPA-1).jpg", newName);
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
