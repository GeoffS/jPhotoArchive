package net.geoffs.jPhotoArchive;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllTests
{
    public static Test suite()
    {
        TestSuite suite = new TestSuite("Test for net.geoffs.jPhotoArchive");
        //$JUnit-BEGIN$
        suite.addTestSuite(FilenameFixerTest.class);
        suite.addTestSuite(MainTest.class);
        suite.addTestSuite(ArchiverBase_Utils_Test.class);
        //$JUnit-END$
        return suite;
    }

}
