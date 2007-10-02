package net.geoffs.jPhotoArchive;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import junit.framework.TestCase;
import net.geoffs.jPhotoArchive.ArchiverBase.JobResults;
import net.geoffs.jPhotoArchive.ImageArchiveDB.Result;


public class MainTest extends TestCase
{
    private static final String TIER1_NAME = "tier1";
    private static final File TIER1_ROOT = new File(TIER1_NAME);
    private static final String TIER2_NAME = "tier2";
    private static final File TIER2_ROOT = new File(TIER2_NAME);
    
    private static final String TEST_PHOTOS_NAME1 = "testData/testPhotos1";
    private static final File TEST_PHOTOS1 = new File(TEST_PHOTOS_NAME1);
    
    private static final String TEST_PHOTOS_NAME2 = "testData/testPhotos2";
    private static final File TEST_PHOTOS2 = new File(TEST_PHOTOS_NAME2);
    
    private static final String TEST_PHOTOS_NAME3 = "testData/testPhotos3";
    private static final File TEST_PHOTOS3 = new File(TEST_PHOTOS_NAME3);
    
    private static final String TEST_PHOTOS_NAME3a = "testData/testPhotos3a";
    private static final File TEST_PHOTOS3a = new File(TEST_PHOTOS_NAME3a);
    
    private static final String SUB_DIR1 = "dir1";
    private static final String SUB_DIR2 = "dir2";
    
    protected void setUp() throws Exception
    {
        System.out.println("\n\n------------ Setup Output: ------------");
        ArchiverBase.shutdownLog();
        rmDir(TIER1_ROOT);
        rmDir(TIER2_ROOT);
        
        ArchiverBase.initLog(TIER1_ROOT);
        makeNewTier(TIER1_ROOT);
        makeNewTier(TIER2_ROOT);
        assertNoPhotosIn(TIER1_ROOT);
        assertNoPhotosIn(TIER2_ROOT);
        System.out.println("\n------ Test Output: -----");
    }
    
    public void testFilenameChangedAndThenFixed() throws SQLException, ClassNotFoundException
    {
        testSimpleArchive();
        
        // (Re)Check one of the files:
        File filesDir = assertHasCorrectNumberOfEnties(TIER1_ROOT, "files", "dir1", 1);
        File testPhotos1Dir = assertHasCorrectNumberOfEnties(filesDir, "testPhotos1", 3);
        File testFile = assertExists(testPhotos1Dir, "IMG_6669_screen.jpg");
        
        // Rename the file checked above:
        File testFileNewName = new File(testPhotos1Dir, "AnotherNameAltogether.jpg");
        assertTrue(testFile.renameTo(testFileNewName));
        
        // Check to make sure the DB and files are inconsistant:
        assertJobFailure(Main.validateFiles(TIER1_ROOT));
        
        // Run the Fix-it Job:
        assertJobSuccess(Main.fixDbForFile(TIER1_ROOT, "dir1\\testPhotos1\\AnotherNameAltogether.jpg"));
        
        // Check to make sure the archive is valid:
        assertValidateDbAndFiles(TIER1_ROOT);
    }
    
    public void testSameFilenameDifferentContents()
    {
        assertJobSuccess(Main.archiveCard(TEST_PHOTOS3, "TestDir", TIER1_ROOT));
        File filesDir = assertExists(TIER1_ROOT, "files");
        File testDir = assertHasCorrectNumberOfEnties(filesDir, "TestDir", 1);
        assertExists(testDir, "IMG_3904_screen.jpg");
        
        // Check that files are on-disk correctly:
        assertJobSuccess(Main.archiveCard(TEST_PHOTOS3a, "TestDir", TIER1_ROOT));
        assertHasCorrectNumberOfEnties(testDir, 2);
        assertExists(testDir, "IMG_3904_screen.jpg");
        assertExists(testDir, "IMG_3904_screen (jPA-1).jpg");
        
        // Check that the DB entries are correct:
        assertBothVersionsAreInDBCorrectly(TIER1_ROOT);
        
        // Check to make sure the archive is valid:
        assertValidateDbAndFiles(TIER1_ROOT);
    }
    
    /* This is bad!  Copy-Paste from testSameFilenameDifferentContents()
     * with additions...
     * REFACTOR!
     */
    public void testSameFilenameDifferentContentsWithBackups()
    {
        // STEP 1a: Add a photo to TIER1 and verify:
        assertJobSuccess(Main.archiveCard(TEST_PHOTOS3, "TestDir", TIER1_ROOT));
        File filesDir = assertExists(TIER1_ROOT, "files");
        File testDir = assertHasCorrectNumberOfEnties(filesDir, "TestDir", 1);
        assertExists(testDir, "IMG_3904_screen.jpg");
        
        // STEP 1b: Backup to TIER2 and verify:
        assertJobSuccess(Main.backup(TIER1_ROOT, TIER2_ROOT));
        
        File filesDir2 = assertExists(TIER2_ROOT, "files");
        File testDir2 = assertHasCorrectNumberOfEnties(filesDir2, "TestDir", 1);
        assertExists(testDir2, "IMG_3904_screen.jpg");
        
        assertTwoTiersAreGood(TIER1_ROOT, TIER2_ROOT);
        
        //STEP 2a: Add a second photo with the same name and different contents
        //         to TIER1 and make sure it is renamed properly:
        assertJobSuccess(Main.archiveCard(TEST_PHOTOS3a, "TestDir", TIER1_ROOT));
        assertHasCorrectNumberOfEnties(testDir, 2);
        assertExists(testDir, "IMG_3904_screen.jpg");
        assertExists(testDir, "IMG_3904_screen (jPA-1).jpg");
        
        // STEP 2b: Check DB for correct file entries:
        assertBothVersionsAreInDBCorrectly(TIER1_ROOT);
        
        // STEP 3: Backup to TIER2 and verify:
        assertJobSuccess(Main.backup(TIER1_ROOT, TIER2_ROOT));
        
        assertHasCorrectNumberOfEnties(testDir2, 2);
        assertExists(testDir2, "IMG_3904_screen.jpg");
        assertExists(testDir2, "IMG_3904_screen (jPA-1).jpg");
        
        assertBothVersionsAreInDBCorrectly(TIER1_ROOT);
        assertBothVersionsAreInDBCorrectly(TIER2_ROOT);
        
        assertTwoTiersAreGood(TIER1_ROOT, TIER2_ROOT);
    }

    private void assertBothVersionsAreInDBCorrectly(File archiveBaseDir)
    {
        JobResults listResults = assertJobSuccess(Main.list(archiveBaseDir));
        assertEquals(0, listResults.getErrors().size());
        assertEquals(2, listResults.getResults().size());
        assertResultsContainFilename( "TestDir\\"+"IMG_3904_screen.jpg",         listResults.getResults());
        assertResultsContainFilename( "TestDir\\"+"IMG_3904_screen (jPA-1).jpg", listResults.getResults());
    }
    
    public void testFileForMd5Sum() throws SQLException, ClassNotFoundException
    {
        testSimpleArchive();
        
        // Check a match:
        JobResults jobResultsMatch = 
            assertJobSuccess(Main.findForMD5Sum(TIER1_ROOT, "2ea55e0237f8e194bd3df18b960c5897"));
        List<Result> matchResults = jobResultsMatch.getResults();
        assertEquals("Wrong number of results", 1, matchResults.size());
        assertResultsContainFilename( "dir1\\testPhotos1\\IMG_6669_screen.jpg", matchResults);
        
        // Check no-match:
        JobResults jobResultsNoMatch = 
            assertJobSuccess(Main.findForMD5Sum(TIER1_ROOT, "NotAnMD5ChecksumNoWayRightLength"));
        List<Result> noMatchResults = jobResultsNoMatch.getResults();
        assertEquals("Wrong number of results", 0, noMatchResults.size());
    }
    
    public void testFindForFilename() throws SQLException, ClassNotFoundException
    {
        testSimpleArchive();
        
        JobResults jobResults = assertJobSuccess(Main.findForFilename(TIER1_ROOT, "0720"));
        
        List<Result> findResults = jobResults.getResults();
        
        assertEquals("Wrong number of resutls", 3, findResults.size());
        
        String baseDir = "dir1\\testPhotos1\\subdir1\\";
        assertResultsContainFilename( baseDir+"IMG_0720.CR2",      findResults);
        assertResultsContainFilename( baseDir+"IMG_0720.xmp",      findResults);
        assertResultsContainFilename( baseDir+"IMG_0720_test.jpg", findResults);
    }
    
    private void assertResultsContainFilename(String expectedFilename, List<Result> findResults)
    {
        for (Result result : findResults)
        {
            if(result.resultRelPath.equals(expectedFilename))
            {
                return;
            }
        }
        // No match, no success...
        fail("Coudn't find a match for "+expectedFilename);
    }

    public void testMove() throws SQLException, ClassNotFoundException
    {
        testSimpleArchive();
        
        assertTestPhotos1AreInTree(TIER1_ROOT, "dir1", "subdir1");
        
        assertJobSuccess(Main.move(TIER1_ROOT, "dir1\\testPhotos1\\subdir1", "dir1\\testPhotos1\\subdir1a"));
        
        assertTestPhotos1AreInTree(TIER1_ROOT, "dir1", "subdir1a");
        
        assertValidateDbAndFiles(TIER1_ROOT);
    }
    
    public void testValidateDbAgainstFiles_And_FixBackup() throws SQLException, ClassNotFoundException
    {
        // Load some stuff into TIER1 and TIER2...
        testSimpleBackup();
        
        // At this point we have two tiers with the same content.
        // Let's screw it up a bit:
        deleteOnePhotoFromTestPhotos1InTree(TIER2_ROOT, SUB_DIR1);
        
        // We'll check to make sure the archive is screwed up just right:
        JobResults errors = assertJobFailure(Main.validateDB(TIER2_ROOT));
        assertTestFileInErrors(errors);
        
        // Now check the tier1 DB against the tier2 files to see if we get the same error:
        errors = assertJobFailure(Main.validateDbAgainstFiles(TIER1_ROOT, TIER2_ROOT));
        assertTestFileInErrors(errors);
        
        // Now we'll fix it up...
        //assertJobSuccess(Main.markMissingFilesAsUnBackedup(TIER1_ROOT, TIER2_ROOT));
        assertJobSuccess(Main.fixBadBackup(TIER1_ROOT, TIER2_ROOT));
        
        // Now everything should be good again...
        assertTwoTiersAreGood(TIER1_ROOT, TIER2_ROOT);
        
        // Finally, we'll re-run the backup to make sure it works properly.
        //    First, double check that the test-file is still gone:
        assertTestFileDoesNotExistIn(TIER2_ROOT, SUB_DIR1);
        //    Next run a backup:
        assertJobSuccess(Main.backup(TIER1_ROOT, TIER2_ROOT));
        //    Check for the file:
        assertTestFileExistsIn(TIER2_ROOT, SUB_DIR1);
        //    Validate both tiers:
        assertTwoTiersAreGood(TIER1_ROOT, TIER2_ROOT);
    }

    public void testBackupWithValidation() throws SQLException, ClassNotFoundException
    {
        // Load files into the tier1 archive
        testSimpleArchive();
        
        // Backup files from tier1 -> tier2 and verify the archives are the same:
        assertJobSuccess(Main.backupWithFullValidation(TIER1_ROOT, TIER2_ROOT));
        assertTestPhotos1AreInTree(TIER1_ROOT, SUB_DIR1);
        assertTestPhotos1AreInTree(TIER2_ROOT, SUB_DIR1);
        
        assertTwoTiersAreGood(TIER1_ROOT, TIER2_ROOT);
    }

    public void testCardArchiveAndBackup()
    {
        assertJobSuccess(Main.archiveCard(TEST_PHOTOS1, "New-Dir-Name", TIER1_ROOT));
        assertTestPhotos1AreInDir(TIER1_ROOT, "New-Dir-Name");
        
        // Validate the DB and files:
        assertJobSuccess(Main.validateFiles(TIER1_ROOT));
        assertJobSuccess(Main.validateDB(TIER1_ROOT));
        
        // Backup Files:
        assertJobSuccess(Main.backup(TIER1_ROOT, TIER2_ROOT));
        assertTestPhotos1AreInDir(TIER2_ROOT, "New-Dir-Name");
        
        assertValidateDbAndFiles(TIER2_ROOT);
    }

    private void assertValidateDbAndFiles(File archiveRoot)
    {
        // Validate the DB and files:
        assertJobSuccess(Main.validateFiles(archiveRoot));
        assertJobSuccess(Main.validateDB(archiveRoot));
    }

    public void testSimpleArchive() throws SQLException, ClassNotFoundException
    {
        testArchive(TEST_PHOTOS1, SUB_DIR1);
    }
    
    public void testSimpleArchiveWithNullSubDir() throws SQLException, ClassNotFoundException
    {
        assertJobSuccess(Main.archiveTree(TEST_PHOTOS1, null, TIER1_ROOT));
        assertTestPhotos1AreInTree(TIER1_ROOT, null);
        
        assertValidateDbAndFiles(TIER1_ROOT);
    }
    
    private void testArchive(File testPhotosDir, String archiveSubDir)
    {
        assertJobSuccess(Main.archiveTree(testPhotosDir, archiveSubDir, TIER1_ROOT));
        assertTestPhotos1AreInTree(TIER1_ROOT, SUB_DIR1);
        
        assertValidateDbAndFiles(TIER1_ROOT);
    }
    
    public void testTwoStepArchive()
    {
        testArchive(TEST_PHOTOS1, SUB_DIR1);
        testArchive(TEST_PHOTOS2, SUB_DIR2);
    }
    
    public void testSimpleBackup() throws SQLException, ClassNotFoundException
    {
        // Load files into the tier1 archive
        testSimpleArchive();
        
        // Backup files from tier1 -> tier2 and verify the archives are the same:
        backupTier1ToTier2AndVerifyTheyAreTheSame();
    }

    private void backupTier1ToTier2AndVerifyTheyAreTheSame()
    {
        assertJobSuccess(Main.backup(TIER1_ROOT, TIER2_ROOT));
        assertTestPhotos1AreInTree(TIER1_ROOT, SUB_DIR1);
        assertTestPhotos1AreInTree(TIER2_ROOT, SUB_DIR1);
        
        assertTwoTiersAreGood(TIER1_ROOT, TIER2_ROOT);
    }
    
    public void testBackupErrorHandling() throws SQLException, ClassNotFoundException, FileNotFoundException, IOException
    {
        // Load files into the tier1 archive
        testSimpleArchive();
        
        // Insert one test file into TIER2's files folder (but no DB):
        /*File srcFile = */copyOnePhotoFromTierToTier(TIER1_ROOT, TIER2_ROOT);
        
        // Check TIER2 to make sure it's invalid:
        JobResults errors1 = assertJobFailure(Main.validateFiles(TIER2_ROOT));
        assertTestFileInErrors(errors1);
        
        // Run the backup.  We expect it to fail.
        JobResults errors2 = assertJobFailure(Main.backup(TIER1_ROOT, TIER2_ROOT));
        assertTestFileInErrors(errors2);
        
//        assertTrue("Couln'd delete the src file: "+srcFile, srcFile.delete());
    }
    
    //==============================================================================================
    //  Helper Classes below
    //vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
    
    private JobResults assertJobSuccess(JobResults results)
    {
        ArchiverBase.printErrors(results);
        assertTrue("Job failed", results.noErrors());
        return results;
//        if(results.noErrors())
//        {
//            return;
//        }
//        else
//        {
//            ArchiverBase.printErrors(results);
//            fail("Job failed");
//        }
    }
    
    private JobResults assertJobFailure(JobResults results)
    {
        ArchiverBase.printErrors(results);
        assertFalse("Job didn't fail", results.noErrors());
        return results;
    }

    private void makeNewTier(final File archiveRootDir) throws SQLException, ClassNotFoundException
    {
        assertJobSuccess(Main.makeNewTier(archiveRootDir));
        assertTrue("", TIER1_ROOT.exists());
    }

    private void rmDir(final File dir)
    {
        if(!dir.exists()) return; 
        
        FileFind.FindVisitor delete = new FileFind.FindVisitor(){
            public void postProcessDir(File dir)
            {
                //System.out.println("Deleting: "+dir);
                assertTrue("Failed to delete dir "+dir, dir.delete());
            }

            public void processFile(File rootPath, String relPath)
            {
                File file = new File(rootPath, relPath);
                //System.out.println("Deleting: "+file);
                assertTrue("Failed to delete file "+file, file.delete());
            }
        };
        
        FileFilter everything = new FileFilter(){
            public boolean accept(File pathname)
            {
                return true;
            }
        };
        
        new FileFind(delete, everything).find(dir);
    }
    
    private void assertNoPhotosIn(File root)
    {
        assertHasCorrectNumberOfEnties(root, "files", 0);
    }
    

    private void assertTestPhotos1AreInDir(File root, String singleDirName)
    {
        File filesDir = assertHasCorrectNumberOfEnties(root, "files", 1);
        
        File singleDir = assertHasCorrectNumberOfEnties(filesDir, singleDirName, 5);
        assertExists(singleDir, "IMG_6669_screen.jpg");
        assertExists(singleDir, "IMG_0720.CR2");
        assertExists(singleDir, "IMG_0720_test.jpg");
        assertExists(singleDir, "IMG_0720.xmp");
        // 'index.html' should not be here.
        assertExists(singleDir, "IMG_1214.JPG");
    }

    private void assertTestPhotos1AreInTree(File root, String subdir)
    {
        assertTestPhotos1AreInTree(root, subdir, "subdir1");
    }
    
    private void assertTestPhotos1AreInTree(File root, String subdir, String movableDirName)
    {
        File filesDir = assertHasCorrectNumberOfEnties(root, "files", subdir, 1);
        
        File testPhotos1Dir = assertHasCorrectNumberOfEnties(filesDir, "testPhotos1", 3);
        assertExists(testPhotos1Dir, "IMG_6669_screen.jpg");
        
        File subDir1 = assertHasCorrectNumberOfEnties(testPhotos1Dir, movableDirName, 3);
        assertExists(subDir1, "IMG_0720.CR2");
        assertExists(subDir1, "IMG_0720_test.jpg");
        assertExists(subDir1, "IMG_0720.xmp");
        // 'index.html' should not be here.
        
        File subDir2 = assertHasCorrectNumberOfEnties(testPhotos1Dir, "subdir2", 1);
        assertExists(subDir2, "IMG_1214.JPG");
    }
    
    private void deleteOnePhotoFromTestPhotos1InTree(File root, String subDir)
    {
        File testFile = assertTestFileExistsIn(root, subDir);
        
        testFile.delete();
        
        assertDoesNotExist(testFile);
    }
    
    private File copyOnePhotoFromTierToTier(File srcTierRoot, File dstTierRoot) throws FileNotFoundException, IOException
    {
        File srcFile = assertTestFileExistsIn(srcTierRoot, SUB_DIR1);
        File dstFile = makeTheOneTestFile(dstTierRoot, SUB_DIR1);
        dstFile.getParentFile().mkdirs();
        
        ArchiverBase.copy(srcFile, dstFile);
        
        return srcFile;
    }
    
    private void assertTestFileInErrors(JobResults errors)
    {
        assertEquals( "Wrong number of errors", 1, errors.getErrors().size());
        assertEquals( "Wrong filename", 
                      "dir1\\testPhotos1\\subdir1\\IMG_0720_test.jpg", 
                      errors.getErrors().get(0).queryRelPath);
    }

    private File assertTestFileExistsIn(File root, String subDirName)
    {
        File testFile = makeTheOneTestFile(root, subDirName);
        return assertExists(testFile); //subDir1, "IMG_0720_test.jpg");
    }
    
    private File assertTestFileDoesNotExistIn(File root, String subDirName)
    {
        File testFile = makeTheOneTestFile(root, subDirName);
        return assertDoesNotExist(testFile);
    }

    private File makeTheOneTestFile(File root, String subDirName)
    {
        File filesDir = new File(root, "files");
        File subDir = new File(filesDir, subDirName);
        File testPhotos1Dir = new File(subDir, "testPhotos1");
        File subDir1 = new File(testPhotos1Dir, "subdir1");
        File testFile = new File( subDir1, "IMG_0720_test.jpg");
        return testFile;
    }

    private File assertHasCorrectNumberOfEnties(File root, String testDirName, int expectedNumberOfEntries)
    {

        File testDir = assertExists(root, testDirName);
        assertHasCorrectNumberOfEnties(testDir, expectedNumberOfEntries);

        return testDir;
    }

    private void assertHasCorrectNumberOfEnties(File testDir, int expectedNumberOfEntries)
    {
        assertEquals("Wrong number of entries in "+testDir, expectedNumberOfEntries, testDir.list().length);
    }
    
    private File assertHasCorrectNumberOfEnties(File root, String subDirName, String testDirName, int expectedNumberOfEntries)
    {
        File subDir = new File(root, subDirName);
        return assertHasCorrectNumberOfEnties(subDir, testDirName, expectedNumberOfEntries);
    }

    private File assertExists(File root, String path)
    {
        File testFile = path==null?root:new File( root, path);
        assertExists(testFile);
        return testFile;
    }

    private File assertExists(File testFile)
    {
        return assertExists(testFile, true);
    }
    
    private File assertDoesNotExist(File testFile)
    {
        return assertExists(testFile, false);
    }
    
    private File assertExists(File testFile, boolean exists)
    {
        assertEquals("File existance wrong: "+testFile, exists, testFile.exists());
        return testFile;
    }
    
    private void assertTwoTiersAreGood(File tier1Root, File tier2Root)
    {
        // Validate the backup DB and files:
        assertJobSuccess(Main.validateFiles(tier1Root));
        assertJobSuccess(Main.validateDB(tier1Root));
        
        // Validate the backup DB and files:
        assertJobSuccess(Main.validateFiles(tier2Root));
        assertJobSuccess(Main.validateDB(tier2Root));
        
        // Validate the two tiers against each other:
        assertJobSuccess(Main.validateTwoTiers(tier1Root, tier2Root));
    }
}
