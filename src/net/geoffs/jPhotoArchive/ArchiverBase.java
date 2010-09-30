package net.geoffs.jPhotoArchive;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.geoffs.jPhotoArchive.ImageArchiveDB.InvalidEntry;
import net.geoffs.jPhotoArchive.ImageArchiveDB.Result;

import com.twmacinta.util.MD5;


public class ArchiverBase
{    
    private static class JpaInputBuffer
    {
        public byte[] buf;
        public String md5AsHex;
        public File file;
    }
    public static final JpaInputBuffer EMPTY_BUFFER;
    static{
        EMPTY_BUFFER = new JpaInputBuffer();
        EMPTY_BUFFER.buf=null; // new byte[0]; ?
        EMPTY_BUFFER.md5AsHex = "------ No MD5 Calculated -------";
        EMPTY_BUFFER.file = null;
    }

    protected static interface DbJob
    {
        void runJob(ImageArchiveDB db, JobResults results) throws SQLException, ClassNotFoundException;
    }

    // For use with copyFileAndAddToDb()
    public static final boolean VERSION_IF_NEEDED = true;
    public static final boolean DO_NOT_VERSION    = false;

    protected static void runJobNoTraps(File archiveRoot, JobResults results, DbJob job) throws SQLException, ClassNotFoundException
    {
        ImageArchiveDB db = null;
        try
        {
            db = openDB(archiveRoot);
            job.runJob(db, results);
        }
        finally
        {
            if(db != null) db.shutdown();
        }
    }
    
    public static JobResults runJob(File archiveRoot, DbJob job)
    {
        JobResults results = new JobResults();
        try
        {
            // This little bit 'o hack allows us to use the exception handling
            // for the database creation command...
            if(archiveRoot!=null)
            {
                runJobNoTraps(archiveRoot, results, job);
            }
            else
            {
                job.runJob(null, results);
            }
        }
        catch (SQLException e)
        {
            showError("Job failed", e);
            results.addError(new ImageArchiveDB.InvalidEntry(e));
        }
        catch (ClassNotFoundException e)
        {
            showError("Job failed", e);
            results.addError(new ImageArchiveDB.InvalidEntry(e));
        }
        
        return results;
    }
    
    public static class JobResults
    {
        private final List<Result> results;
        private final List<InvalidEntry> errors;
        private int numberOfFilesCopied;
        
        public JobResults()
        {
            this.errors = new ArrayList<InvalidEntry>();
            this.results = new ArrayList<Result>();
            numberOfFilesCopied = 0;
        }
        
        public synchronized void addError(InvalidEntry error)
        {
            this.errors.add(error);
        }
        
        public synchronized void addResult(Result result)
        {
            this.results.add(result);
        }
        
        public synchronized void addError(final String md5Sum, 
                             final String queryRelPath, 
                             final String resultRelPath, 
                             final String reason)
        {
            addError(new InvalidEntry(md5Sum, queryRelPath, resultRelPath, reason));
        }
        
        public synchronized void addError(final String reason)
        {
            addError(new InvalidEntry(reason));
        }
        
        public synchronized void addError(final Exception cause)
        {
            addError(new InvalidEntry(cause));
        }
        
        public synchronized void addResult(final String md5Sum, 
                              final String resultRelPath)
        {
            addResult(new Result(md5Sum, resultRelPath));
        }
        
        public synchronized JobResults add(JobResults moreResults)
        {
            errors.addAll(moreResults.getErrors());
            results.addAll(moreResults.getResults());
            numberOfFilesCopied += moreResults.numberOfFilesCopied;
            
            return this;
        }
        
        public synchronized List<Result> getResults()
        {
            return results;
        }

        public synchronized boolean noErrors()
        {
            return ArchiverBase.noErrors(errors);
        }

        public synchronized List<InvalidEntry> getErrors()
        {
            return errors;
        }

        public synchronized int numFilesCopied()
        {
            return numberOfFilesCopied;
        }

        public synchronized void anotherFileCopied()
        {
            numberOfFilesCopied++;
        }
    }

    protected static ImageArchiveDB openDB(File archiveRootDir) throws SQLException, ClassNotFoundException
    {
        return new ImageArchiveDB(makeDbDirectory(archiveRootDir), log);
    }

    protected static File makeDbDirectory(File archiveRootDir)
    {
        return new File(archiveRootDir, "db");
    }
    
    private static File makeLogsDir(File archiveRootDir)
    {
        return new File(archiveRootDir, "logs");
    }
    
    public static String calcMD5For(File file, final Object lock)
    {
        final String md5;
        synchronized(lock)
        {
            System.out.println(lock+" - Start calcMD5For("+file+")");
            md5 = md5asHexFor(file);
            System.out.println(lock+" - Done calcMD5For("+file+")");
        }
        
        return md5;
    }
    
    public static String calcMD5For(File file)
    {
        System.out.println("Un-synchronized calcMD5For("+file+")");
        return md5asHexFor(file);
    }
    
    private static String md5asHexFor(final File file)
    {
        try
        {
            return MD5.asHex(MD5.getHash(file));
        }
        catch (IOException e)
        {
            showError("No MD5 sum calculated for "+file, e);
            return "------ No MD5 Calculated -------";
        }
    }
    
    private static JpaInputBuffer readFileAndMD5(final File file, final Object lock)
    {
        
            JpaInputBuffer in = new JpaInputBuffer();
            in.file = file;
            long length = file.length();
            if(length > (Integer.MAX_VALUE-10))
            {
                showError("File ("+file+") is too large.");
            }
            int len = (int)length;
            in.buf = new byte[len+10];
            
            int bytesRead;
            try
            {
                synchronized (lock)
                {
                    System.out.println(lock+" - Start readFileAndMD5("+file+")");
                    FileInputStream fis = new FileInputStream(file);
                    bytesRead = fis.read(in.buf);
                    fis.close();
                    System.out.println(lock+" - Done readFileAndMD5("+file+")");
                }
            }
            catch (IOException e)
            {
                showError("Error reading file "+file, e);
                return EMPTY_BUFFER;
            }

            if(bytesRead != len)
            {
                showError("Error reading file, bytes read ("+bytesRead+") not equal to file length ("+len+")");
                return EMPTY_BUFFER;
            }

            MD5 md5 = new MD5();
            md5.Update(in.buf);
            in.md5AsHex = MD5.asHex(md5.Final());

            return in;
        
    }
    
    protected static void showError(String msg, String path, Exception e)
    {
        showError(msg+path, e);
    }

    private static void showError(String msg, Exception e)
    {
        log.error(msg, e);
        System.out.println(msg);
        e.printStackTrace();
        System.out.println("-------------------------\n");
    }
    
    private static void showError(String msg)
    {
        log.error(msg);
        System.out.println(msg);
        System.out.println("-------------------------\n");
    }
    
    protected static class DbAndCopyVisitor1 extends FileFind.FileOnlyVisitor
    {
        private final ImageArchiveDB db;
        private final File archinveRootDir;
        private final File dstDir;
        private final JobResults results;
        private final JpaExecutor executor;
        
        DbAndCopyVisitor1(final ImageArchiveDB db, 
                          final File archiveRootDir, 
                          final String destinationDir, 
                          final JobResults results,
                          final JpaExecutor executor)
        {
            this.db = db;
            this.archinveRootDir = archiveRootDir;
            this.dstDir = new File(destinationDir);
            this.results = results;
            this.executor = executor;
        }
        
        public void processFile(final File rootPath, final String relPath)
        {
            Runnable procFile = new Runnable(){
                @Override
                public void run()
                {
                    processFileWorker(rootPath, relPath);
                }
            };
            
            System.out.println("Submitting "+rootPath+", "+relPath);
            executor.submit(procFile, results);
        }

        private void processFileWorker(final File rootPath, final String relPath)
        {
            System.out.print(".");
            
            File srcFile = new File(rootPath, relPath);
            System.out.println("Processing file: "+srcFile);
            
            final JpaInputBuffer srcData = readFileAndMD5(srcFile, Locks.SRC_DEVICE_ACCESS);
            if(srcData == EMPTY_BUFFER) return; // We've logged the error already.
            
            String dstRelPath = new File(dstDir, new File(relPath).getName()).getPath();
            try
            {
                saveToFileAndAddToDb(VERSION_IF_NEEDED, dstRelPath, srcData, archinveRootDir, db, results);
            }
            catch (Exception e)
            {
                results.addError(e);
            }
        }
    }
    
    protected static class DbAndCopyVisitor extends FileFind.FileOnlyVisitor
    {
        private final ImageArchiveDB db;
        private final File archinveRootDir;
        private final File archiveSubDir;
        private final JobResults results;
        
        DbAndCopyVisitor(ImageArchiveDB db, File archiveRootDir, String archiveSubDir, JobResults results)
        {
            this.db = db;
            this.archinveRootDir = archiveRootDir;
            this.archiveSubDir = archiveSubDir==null?null:new File(archiveSubDir);
            this.results = results;
        }

        public void processFile(File rootPath, String relPath)
        {
            System.out.print(".");
            File fullPath = new File(rootPath, relPath);
            String md5sum = calcMD5For(fullPath);
            String dstRelPath = new File(archiveSubDir, relPath).getPath();
            try
            {
                copyFileAndAddToDb(fullPath, VERSION_IF_NEEDED, dstRelPath, md5sum, archinveRootDir, db, results);
            }
            catch (Exception e)
            {
                results.addError(e);
            }
        }
    }
    
    public static void save(final JpaInputBuffer src, final File toFile)
        throws FileNotFoundException, IOException
    {
        log.msg("Save: "+src.file+"->"+toFile);
        
        FileOutputStream to = new FileOutputStream(toFile);
        try
        {
            to.write(src.buf);
        }
        finally
        {
            close(to);
        }
    }

    private static void close(FileOutputStream to)
    {
        if(to != null)
        {
            try
            {
                to.close();
            }
            catch (IOException e)
            {
                System.out.println("to.close() - Shouldn't happen!");
                e.printStackTrace();
            }
        }
    }
    
    public static void copy(final File fromFile, final File toFile) 
        throws FileNotFoundException, IOException
    {
        log.msg("Copy: "+fromFile+"->"+toFile);
        
        InputStream inputStream = new FileInputStream(fromFile);
        FileOutputStream to = new FileOutputStream(toFile);
        try
        {
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1)
            {
                to.write(buffer, 0, bytesRead); // write
            }

            toFile.setReadOnly();
        }
        finally
        {
            if(inputStream != null)
            {
                try
                {
                    inputStream.close();
                }
                catch (IOException e)
                {
                    System.out.println("inputStream.close() - Shouldn't happen!");
                    e.printStackTrace();
                }
            }
            close(to);
        }
    }
    
    protected static void copyFileAndAddToDb(File srcFileFullPath,
                                             boolean insertVersionNumberIfneeded,
                                             String dstFileRelPathName,
                                             String md5sum,
                                             File archiveRootDir, 
                                             ImageArchiveDB db, 
                                             JobResults results) 
    throws SQLException, FileNotFoundException, IOException
{
        File archiveFilesDir = makeFilesDirFrom(archiveRootDir);
        
        File dstFileRelPath = new File(dstFileRelPathName);
        String dstFileName = dstFileRelPath.getName();
        File dstFileRelParent = dstFileRelPath.getParentFile();
        
        File dstFileFullPath = new File(archiveFilesDir, dstFileRelPathName);
        File dstDir = dstFileFullPath.getParentFile();
        
        // Sync on DST_DEVICE_ACCESS?
        dstDir.mkdirs();

        String existingEntry = db.alreadyExists(md5sum);
        if(existingEntry!=null)
        {
            String msg = md5sum+": "+srcFileFullPath+" already exists as "+existingEntry;
            log.warning(msg);
            System.out.println("\n"+msg);
        }
        else
        {
            if(insertVersionNumberIfneeded)
            {
                dstFileName = insertCopyNumberIfNecessary(dstDir, dstFileName);
                
                // Recreate the necessary pieces for the DB and copy operations:
                dstFileRelPathName = new File(dstFileRelParent, dstFileName).getPath();
                dstFileFullPath    = new File(dstDir,           dstFileName);
            }
            
            synchronized (Locks.SRC_DEVICE_ACCESS)
            {
                System.out.println("SRC_DEVICE_ACCESS - Start copy("+srcFileFullPath+", "+dstFileFullPath+")");
                synchronized (Locks.DST_DEVICE_ACCESS)
                {
                    System.out.println("DST_DEVICE_ACCESS - Start copy("+srcFileFullPath+", "+dstFileFullPath+")");
                    copy(srcFileFullPath, dstFileFullPath);
                    System.out.println("DST_DEVICE_ACCESS - Done copy("+srcFileFullPath+", "+dstFileFullPath+")");
                }
                System.out.println("SRC_DEVICE_ACCESS - Done copy("+srcFileFullPath+", "+dstFileFullPath+")");
            }
            
            results.anotherFileCopied();

            final String toMD5Sum = calcMD5For(dstFileFullPath, Locks.DST_DEVICE_ACCESS);
            
            if(md5sum.equals(toMD5Sum))
            {
                db.insert(md5sum, dstFileRelPathName);
            }
            else
            {
                String msg = "MD5s don't match: "+srcFileFullPath+":"+md5sum+" != "+dstFileFullPath+":"+toMD5Sum;
                log.error(msg);
                System.out.println("Error: "+msg);
            }
        }
    }
    
    protected static void saveToFileAndAddToDb(
            boolean insertVersionNumberIfneeded,
            String dstFileRelPathName,
            JpaInputBuffer srcData,
            File archiveRootDir, 
            ImageArchiveDB db, 
            JobResults results) 
    throws SQLException, FileNotFoundException, IOException
    {
        File archiveFilesDir = makeFilesDirFrom(archiveRootDir);

        File dstFileRelPath = new File(dstFileRelPathName);
        String dstFileName = dstFileRelPath.getName();
        File dstFileRelParent = dstFileRelPath.getParentFile();

        File dstFileFullPath = new File(archiveFilesDir, dstFileRelPathName);
        File dstDir = dstFileFullPath.getParentFile();

        // Sync on DST_DEVICE_ACCESS?
        dstDir.mkdirs();

        String existingEntry = db.alreadyExists(srcData.md5AsHex);
        if(existingEntry!=null)
        {
            String msg = srcData.md5AsHex+": "+srcData.file+" already exists as "+existingEntry;
            log.warning(msg);
            System.out.println("\n"+msg);
        }
        else
        {
            if(insertVersionNumberIfneeded)
            {
                dstFileName = insertCopyNumberIfNecessary(dstDir, dstFileName);

                // Recreate the necessary pieces for the DB and copy operations:
                dstFileRelPathName = new File(dstFileRelParent, dstFileName).getPath();
                dstFileFullPath    = new File(dstDir,           dstFileName);
            }

            synchronized (Locks.DST_DEVICE_ACCESS)
            {
                System.out.println("DST_DEVICE_ACCESS - Start save("+srcData.file+", "+dstFileFullPath+")");
                save(srcData, dstFileFullPath);
                System.out.println("DST_DEVICE_ACCESS - Done save("+srcData.file+", "+dstFileFullPath+")");
            }

            results.anotherFileCopied();

            final String toMD5Sum = calcMD5For(dstFileFullPath, Locks.DST_DEVICE_ACCESS);

            if(srcData.md5AsHex.equals(toMD5Sum))
            {
                db.insert(srcData.md5AsHex, dstFileRelPathName);
            }
            else
            {
                String msg = "MD5s don't match: "+srcData.file+":"+srcData.md5AsHex+" != "+dstFileFullPath+":"+toMD5Sum;
                log.error(msg);
                System.out.println("Error: "+msg);
            }
        }
}

    private static String insertCopyNumberIfNecessary(File dstDir, String dstFilename)
    {
        File[] existingFiles = dstDir.listFiles();
        String[] existingFileanames = new String[existingFiles.length];
        for (int i = 0; i < existingFiles.length; i++)
        {
            existingFileanames[i] = existingFiles[i].getName();
        }
        
        //String dstFilename = dstFileFullPath.getName();
        
        if(!contains(existingFileanames, dstFilename))
        {
            return dstFilename;
        }
        else
        {
            String versionedName = makeVersionedName(dstFilename, existingFileanames);
            
            String msg = "Destination "+dstFilename+" in "+dstDir+" already exists with different contents, "+
                          "renaming to: "+versionedName;
            log.warning(msg);
            System.out.println("\n"+msg);
            
            return versionedName;
        }
    }

    protected static File makeFilesDirFrom(File archiveRootDir)
    {
        return new File(archiveRootDir, "files");
    }
    
    protected static void makeNewJobEntryInDB(ImageArchiveDB db) throws SQLException
    {
        try
        {
            int jobNum = db.newJob();
            log.msg("New Job: " + jobNum);
            System.out.println("Job number "+jobNum);
        }
        catch (SQLException e)
        {
            log.error("Error getting job number", e);
            throw new SQLException("Error getting job number", e);
        }
    }

    public static void printErrors(JobResults results)//List<ImageArchiveDB.InvalidEntry> errors)
    {
        List<InvalidEntry> errors = results.getErrors();
        int numErrors = errors.size();
        if(numErrors == 0)
        {
            System.out.println("All Good!");
        }
        else
        {
            log.errors(errors);
            System.out.println(numErrors+" Errors were found:");
            for (InvalidEntry err : errors)
            {
                if(err.queryRelPath!=null || err.md5Sum!=null)
                {
                    System.out.print(err.queryRelPath+"["+err.md5Sum+"]");
                }
                if(err.resultRelPath!=null)
                {
                    System.out.print(" -> "+err.resultRelPath);
                }
                System.out.println(": "+err.reason);
            }
        }
    }

    protected static void makeNewDb(final File destinationDir) throws SQLException, ClassNotFoundException
    {
        File dbDir = makeDbDirectory(destinationDir);
        System.out.println("Make new data-base in "+dbDir);
        ImageArchiveDB.createDB(dbDir, log);
    }
    
    protected static JPAlog log;
    protected static void initLog(File archiveRootDir) throws IOException
    {
        log = new SimpleFileJPAlog(makeLogsDir(archiveRootDir));
    }
    
    protected static void shutdownLog()
    {
        if(log != null) log.close();
    }

    protected static boolean noErrors(List<ImageArchiveDB.InvalidEntry> errors)
    {
        if(errors==null || errors.size()==0)
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    protected static String getArgOrNull(String[] args, int argIndex)
    {
        int lastIndex = args.length-1;
        if(argIndex > lastIndex) 
        {
            return null;
        }
        else
        {
            return args[argIndex];
        }
    }

    protected static class ImageFilter implements FileFilter
    {
        private static final String[] extensions = {"jpg", "jpeg", "cr2", "psd", "nef", "tif", "tiff", "xmp", "mov", "thm" };
        
        public boolean accept(File file)
        {
            String ext = getExtension(file.getName());
            
            for (int i = 0; i < extensions.length; i++)
            {
                if(ext.equalsIgnoreCase(extensions[i])) return true;
            }
            
            return false;
        }
    
        private String getExtension(String name)
        {
            String[] pieces = name.split("\\.");
            return pieces[pieces.length-1];
        }
    }
    
    protected static class FilesValidationVisitor extends FileFind.FileOnlyVisitor
    {
        private final ImageArchiveDB db;
        private final List<ImageArchiveDB.InvalidEntry> invalidFiles;
        
        FilesValidationVisitor(ImageArchiveDB db, File destinationDir, JobResults results) //List<ImageArchiveDB.InvalidEntry> errorCollector)
        {
            this.db = db;
            this.invalidFiles = results.getErrors();
        }
        
        public void processFile(File rootPath, String relPath)
        {
            System.out.print(".");
            String md5sum = calcMD5For(new File(rootPath, relPath));
            try
            {
                db.validate(md5sum, relPath, invalidFiles);
            }
            catch (SQLException e)
            {
                showError("Error validating", relPath, e);
                e.printStackTrace();
            }
        }

//        public List<ImageArchiveDB.InvalidEntry> getInvalidFiles()
//        {
//            return invalidFiles;
//        }
    }


    public static String makeVersionedName(String startingName, String[] existingFiles)
    {
//        if(!contains(existingFiles, startingName))
//        {
//            return startingName;
//        }
//        else
//        {
            int copyNum = 1;
            String candidateFilename = makeCopyOfFilename(startingName, copyNum);
            while (contains(existingFiles, candidateFilename))
            {
                copyNum++;
                candidateFilename = makeCopyOfFilename(startingName, copyNum);
            }
            return candidateFilename;
//        }
    }

    private static String makeCopyOfFilename(String startingName, int copyNum)
    {
        int trailingDotIndex = startingName.lastIndexOf('.');
        String name = startingName.substring(0, trailingDotIndex);
        String ext = startingName.substring(trailingDotIndex+1);
        
        String copyNumber = " (jPA-"+copyNum+")";
        
        return name+copyNumber+"."+ext;
    }
    
    private static boolean contains(String[] existingFiles, String testElement)
    {
        for (int i = 0; i < existingFiles.length; i++)
        {
            if(testElement.equals(existingFiles[i]))
            {
                return true;
            }
        }
        return false;
    }
    
    private static final Pattern versionNumberPattern = Pattern.compile("^.* \\(jPA-([0-9]+)\\)\\..*$");
    public static final String APP_NAME = "jPhotoArchive ";
    
    public static boolean hasPreviousVersionNumber(String name)
    {
//        String[] chunks = name.split("^.* \\(jPA-[0-9]+\\)\\..*$");
//        return chunks.length == 0;
        return versionNumberPattern.matcher(name).matches();
    }
    
    public static int getVersionNumber(String filename)
    {
        Matcher matcher = versionNumberPattern.matcher(filename);
        if( matcher.find() )
        {
            String num = matcher.group(1);
            return Integer.parseInt(num);
        }
        else
        {
            return -1;
        }
    }
}
