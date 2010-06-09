package net.geoffs.jPhotoArchive;

import net.geoffs.jPhotoArchive.ArchiverBase.JobResults;
import net.geoffs.jPhotoArchive.ImageArchiveDB.Result;
import junit.framework.TestCase;

public class JobResultsTest extends TestCase
{
    public void testEmpty()
    {
        JobResults out = new JobResults();
        
        assertResultsSummaryIs(0, 0, 0, true, out);
    }
    
    final static Result misc1Result = new Result("0dcc5b84d528a260314a238aa0890c32", 
                                                 "dir1\\testPhotos1\\subdir1\\IMG_0720_test.jpg", 
                                                 "Just 'cause!");
    
    final static Result misc2Result = new Result("2ab26acd47122b4c7d64fe41ccb35c2f",
                                                 "dir1\\testPhotos1\\subdir1\\MVI_1234.MOV",
                                                 "Reason #2...");
    
    public void testSingleAddResult()
    {
        JobResults out = new JobResults();
        out.addResult(misc1Result);
        
        out.anotherFileCopied();
        out.anotherFileCopied();
        out.anotherFileCopied();
        
        assertResultsSummaryIs(3, 1, 0, true, out);
        
        assertTrue("Missing result object", out.getResults().contains(misc1Result));
    }
    
    public void testAddResults()
    {
        // miscResult1 and no files:
        JobResults firstResults = new JobResults();
        firstResults.addResult(misc1Result);
        
        // miscResult2 and two files:
        JobResults secondResults = new JobResults();
        firstResults.addResult(misc2Result);
        secondResults.anotherFileCopied();
        secondResults.anotherFileCopied();
        
        // No results and four files:
        JobResults thirdResults = new JobResults();
        thirdResults.anotherFileCopied();
        thirdResults.anotherFileCopied();
        thirdResults.anotherFileCopied();
        thirdResults.anotherFileCopied();
        
        JobResults out = new JobResults();
        out.add(firstResults);
        out.add(secondResults);
        out.add(thirdResults);
        
        assertResultsSummaryIs(0+2+4, 1+1, 0, true, out);
        
        assertTrue("Missing misc1Result", out.getResults().contains(misc1Result));
        assertTrue("Missing misc2Result", out.getResults().contains(misc2Result));
    }

    private void assertResultsSummaryIs(final int expectedNumFilesCopied,
                                        final int expectedNumResults,
                                        final int expectedNumErrors,
                                        final boolean expectedNoErrors,
                                        final JobResults testee)
    {
        assertEquals("Wrong number of copied files", 
                     expectedNumFilesCopied,
                     testee.numFilesCopied());
        
        assertEquals("Wrong number of results", 
                     expectedNumResults, 
                     testee.getResults().size());
        
        assertEquals("Wrong number of errors", 
                     expectedNumErrors, 
                     testee.getErrors().size());
        
        assertEquals("noErrors() wrong", 
                     expectedNoErrors, 
                     testee.noErrors());
    }
}
