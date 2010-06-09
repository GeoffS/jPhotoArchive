package net.geoffs.jPhotoArchive;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import net.geoffs.jPhotoArchive.ImageArchiveDB.InvalidEntry;


public class Main extends ArchiverBase
{
    public static void main(String[] args) throws SQLException, ClassNotFoundException, IOException
    {
        final String commandName = args[0];
        final File primaryArchiveRootDir = new File(args[1]);
        
        initLog(primaryArchiveRootDir);
        
        log.commandLine(args);
        
        final int argShift = 2;
        
        final JobResults success;
        if(commandName.equalsIgnoreCase("archiveTree"))
        {
            final File startingPoint  = new File(args[0+argShift]);
            String archiveSubDir = getArgOrNull(args, 1+argShift);
            success = archiveTree(startingPoint, archiveSubDir, primaryArchiveRootDir);
        }
        else if(commandName.equalsIgnoreCase("archiveCard"))
        {
            final File startingPoint  = new File(args[0+argShift]);
            final String newDirName =            args[1+argShift];
            success = archiveCard(startingPoint, newDirName, primaryArchiveRootDir);
        }
        else if(commandName.equalsIgnoreCase("list"))
        {
            success = list(primaryArchiveRootDir);
        }
        else if(commandName.equalsIgnoreCase("new"))
        {
            success = makeNewTier(primaryArchiveRootDir);
        }
        else if(commandName.equalsIgnoreCase("validateFiles"))
        {
            
            success = validateFiles(primaryArchiveRootDir);
        }
        else if(commandName.equalsIgnoreCase("backup"))
        {
            final File dstArchiveDir = new File(args[0+argShift]);
            success = backup(primaryArchiveRootDir, dstArchiveDir);
        }
        else if(commandName.equalsIgnoreCase("validateDB"))
        {
            success = validateDB(primaryArchiveRootDir);
        }
        else if(commandName.equalsIgnoreCase("validateTiers"))
        {
            final File secondLevelArchiveDir = new File(args[0+argShift]);
            success = validateTwoTiers(primaryArchiveRootDir, secondLevelArchiveDir);
        }
        else if(commandName.equalsIgnoreCase("backupAndValidate"))
        {
            final File secondLevelArchiveDir = new File(args[0+argShift]);
            
            success = backupWithFullValidation(primaryArchiveRootDir, secondLevelArchiveDir);
        }
        else if(commandName.equalsIgnoreCase("fixBackup"))
        {
            final File dstArchiveDir = new File(args[0+argShift]);
            success = fixBadBackup(primaryArchiveRootDir, dstArchiveDir);
        }
        else if(commandName.equalsIgnoreCase("move"))
        {
            final String currentDirName = args[0+argShift];
            final String newDirName     = args[1+argShift];
            success = move(primaryArchiveRootDir, currentDirName, newDirName);
        }
        else if(commandName.equalsIgnoreCase("findMD5"))
        {
            final String md5Sum = args[0+argShift];
            success = findForMD5Sum(primaryArchiveRootDir, md5Sum);
        }
        else if(commandName.equalsIgnoreCase("findfile"))
        {
            final String filenameFragment = args[0+argShift];
            success = findForFilename(primaryArchiveRootDir, filenameFragment);
        }
        else if(commandName.equalsIgnoreCase("fixFileInDb"))
        {
            final String relativeFilePath = args[0+argShift];
            success = fixDbForFile(primaryArchiveRootDir, relativeFilePath);
        }
//        else if(commandName.equalsIgnoreCase(""))
//        {
//            
//        }
        else
        {
            String msg = "'"+commandName+"' is not a valid command.";
            log.msg(msg);
            System.out.println(msg);
            success = new JobResults();
            success.addError(new ImageArchiveDB.InvalidEntry(null, null, null, msg));
        }
        
        if(success.noErrors())
        {
            log.exit("Program terminated with no reported errors.");
            System.out.println("Program terminated with no reported errors.");
        }
        else
        {
            System.out.println("Program terminated with errors.");
            printErrors(success);
        }
    }

    static JobResults backupWithFullValidation(final File firstLevelArchiveDir, final File secondLevelArchiveDir)
    {
        log.msg(">>> Backup With Full Validation");
        return 
            print("=== PRE-VALIDATION:  =======================================\n").add(
            validateFiles(firstLevelArchiveDir).add(
            validateFiles(secondLevelArchiveDir).add(
            validateTwoTiers(firstLevelArchiveDir, secondLevelArchiveDir).add(
            print("\n\n=== BACKUP:  =======================================\n").add(
            backup(firstLevelArchiveDir, secondLevelArchiveDir).add(
            print("\n\n=== POST-VALIDATION:  =======================================\n").add(
            validateFiles(firstLevelArchiveDir).add(
            validateFiles(secondLevelArchiveDir).add(
            validateTwoTiers(firstLevelArchiveDir, secondLevelArchiveDir) )))))))));
    }
    
    static JobResults print(String msg)
    {
        System.out.println(msg);
        return new JobResults();
    }

    static JobResults makeNewTier(final File destinationDir) throws SQLException, ClassNotFoundException
    {
        log.msg(">>> Make New Tier "+destinationDir);
        return runJob(null, new DbJob(){
            public void runJob(ImageArchiveDB db, JobResults results) throws SQLException, ClassNotFoundException
            {
                // Note: db parameter is null.
                
                makeNewDb(destinationDir);
                makeFilesDirFrom(destinationDir).mkdirs();
            }
        });
        
    }

    static JobResults validateTwoTiers(final File firstLevelArchiveDir, 
                                       final File secondLevelArchiveDir)
    {
        log.msg(">>> Cross Validate Two Tiers: "+firstLevelArchiveDir+" => "+secondLevelArchiveDir);
        System.out.println("Cross-validate "+firstLevelArchiveDir+" to "+secondLevelArchiveDir+":");
        System.out.println("---------------");
                
        JobResults jobSuccess = ArchiverBase.runJob(firstLevelArchiveDir, new DbJob(){
            public void runJob(final ImageArchiveDB firstLevelDb, final JobResults results) throws SQLException, ClassNotFoundException
            {
                final Map<String, String> firstLevelEntries = firstLevelDb.getBackedupEntries();
                
                ArchiverBase.runJobNoTraps(secondLevelArchiveDir, results, new DbJob(){
                    public void runJob(ImageArchiveDB secondLevelDb, final JobResults results) throws SQLException, ClassNotFoundException
                    {
                        Map<String, String> secondLevelEntries = secondLevelDb.getEntries();
                        
                        for (Iterator<String> iter = firstLevelEntries.keySet().iterator(); iter.hasNext();)
                        {
                            System.out.print(".");
                            String md5Sum = iter.next();
                            String firstLevelFilename = firstLevelEntries.get(md5Sum);

                            String secondLevelFilename = secondLevelEntries.remove(md5Sum);
                            if(secondLevelFilename==null)
                            {
                                results.addError(md5Sum, firstLevelFilename, null, "entry not found in "+secondLevelArchiveDir);
                            }
                            else if(!firstLevelFilename.equals(secondLevelFilename))
                            {
                                results.addError(md5Sum, firstLevelFilename, secondLevelFilename, "filenames not the same");
                            }
                        }
                        for (String unaccountedForMd5Sum : secondLevelEntries.keySet())
                        {
                            System.out.print(".");
                            String unaccountedForFile = secondLevelEntries.get(unaccountedForMd5Sum);
                            results.addError(unaccountedForMd5Sum, unaccountedForFile, null, "unaccounted for in "+secondLevelArchiveDir);
                        }
                        System.out.println();
                        printErrors(results);
                    }
                });
            }
        });
        System.out.println("\nDone.\n");
        return jobSuccess;
    }

    static JobResults backup(final File srcArchiveDir, final File dstArchiveDir)
    {
        log.msg(">>> Backup One Tier to Another: "+srcArchiveDir+" => "+dstArchiveDir);
        System.out.println("Backup "+srcArchiveDir+" to "+dstArchiveDir+":");
        System.out.println("---------------");
        
        final File srcArchiveFilesDir = makeFilesDirFrom(srcArchiveDir);
        
        return ArchiverBase.runJob(srcArchiveDir, new DbJob(){
            public void runJob(final ImageArchiveDB srcDb, JobResults results) throws SQLException, ClassNotFoundException
            {
                ArchiverBase.runJobNoTraps(dstArchiveDir, results, new DbJob(){
                    public void runJob(ImageArchiveDB dstDb, JobResults results) throws SQLException, ClassNotFoundException
                    {
                        makeNewJob(dstDb);
                        
                        Map files = srcDb.getUnbackedupEntries();
                        for (Iterator iter = files.keySet().iterator(); iter.hasNext();)
                        {
                            System.out.print(".");
                            String md5Sum = (String) iter.next();
                            String fileRelPath = (String) files.get(md5Sum);
                            
                            File srcFileFullPath = new File(srcArchiveFilesDir, fileRelPath);

                            try
                            {
                                copyFileAndAddToDb(srcFileFullPath, DO_NOT_VERSION, 
                                                   fileRelPath, 
                                                   md5Sum, 
                                                   dstArchiveDir, dstDb,
                                                   results);
                                srcDb.setAsBackedUp(md5Sum);
                            }
                            catch (Exception e)
                            {
                                results.addError(md5Sum, fileRelPath, null, "Backup failed: "+e.toString());
                                log.error("Error in backup", e);
                            }
                            
                        }
                        System.out.println();
                    }
                });
            }
        });
    }

    static JobResults validateFiles(final File archiveRootDir)
    {
        log.msg(">>> Validate Files in "+archiveRootDir);
        
        return runJob(archiveRootDir, new DbJob()
        {
            public void runJob(ImageArchiveDB db, JobResults errors)
            {
                File filesDir = makeFilesDirFrom(archiveRootDir);;
                FilesValidationVisitor dbVisitor = new FilesValidationVisitor(db, filesDir, errors);

                FileFind finder = new FileFind(dbVisitor, new ImageFilter());

                System.out.println("Validate Files in "+archiveRootDir+":");
                System.out.println("---------------");
                finder.find(archiveRootDir);
                System.out.println("\nDone scanning.");
                
                printErrors(errors);
                System.out.println("\nJob Complete.\n");
            }   
        });
    }
    
    static JobResults validateDB(final File archiveRootDir)
    {
        return validateDbAgainstFiles(archiveRootDir, archiveRootDir);
    }
    
    static JobResults list(final File archiveRootDir)
    {
        log.msg(">>> List DB "+archiveRootDir);
        
        return runJob(archiveRootDir, new DbJob()
        {
            public void runJob(ImageArchiveDB db, JobResults results) throws SQLException
            {
                System.out.println("Listing "+archiveRootDir+":");
                System.out.println("---------------");
                db.dump(results);
                System.out.println("\nDone.\n");
            }   
        });
    }
    
    public static JobResults move(final File archiveRootDir, final String currentRelPath, final String newRelPath)
    {
        log.msg(">>> Move "+currentRelPath+" to "+newRelPath+" in "+archiveRootDir);

        return runJob(archiveRootDir, new DbJob()
        {
            public void runJob(ImageArchiveDB db, JobResults results) throws SQLException
            {
                File filesDir = makeFilesDirFrom(archiveRootDir);
                
                File currFile = new File(filesDir, currentRelPath);
                File newFile  = new File(filesDir, newRelPath);
                
                File newRelFile = new File(newRelPath); // for later...
                
                if((currFile.exists() && currFile.isDirectory()) == false)
                {
                    String msg = "Starting file ("+currFile+") doesn't exist or isn't a directory.";
                    log.error(msg);
                    results.addError(msg);
                    return;
                }
                
                if(newFile.exists())
                {
                    String msg = "New file ("+newFile+") already exists.";
                    log.error(msg);
                    results.addError(msg);
                    return;
                }
                
                boolean moved = currFile.renameTo(newFile);
                if(moved==false)
                {
                    String msg = "Couldn't rename "+currFile+" to "+newFile;
                    log.error(msg);
                    results.addError(msg);
                    return;
                }
                
                Map<String, String> entries = db.getEntries();
                for( Entry<String, String>entry : entries.entrySet())
                {
                    String oldPath = entry.getValue();
                    if(oldPath.startsWith(currentRelPath))
                    {
                        String tail = oldPath.substring(currentRelPath.length());
                        String newPath = new File(newRelFile, tail).getPath();
                        System.out.println("Renaming: "+oldPath+" -> "+newPath);
                        db.update(entry.getKey(), newPath);
                    }
                }
            }   
        });
    }

    /**
     * This command archives the image files under a starting directory (startingPoint) 
     * into the archive located under destinationDir.  The directory-structure of the 
     * source is retained in the archive.
     * 
     * @param startingPoint
     * @param archiveSubDir 
     * @param destinationDir
     * @return
     */
    static JobResults archiveTree(final File startingPoint, final String archiveSubDir, final File destinationDir)
    {
        log.msg(">>> Archive Directory Tree Starting at "+startingPoint+" => "+destinationDir+" in "+archiveSubDir);
        
        return runJob(destinationDir, new DbJob()
        {
            public void runJob(ImageArchiveDB db, JobResults results) throws SQLException
            {
                makeNewJob(db);
                
                FileFind.FindVisitor dbVisitor = 
                    new DbAndCopyVisitor(db, destinationDir, archiveSubDir, results);

                FileFind finder = new FileFind(dbVisitor, new ImageFilter());

                System.out.println("Archive "+startingPoint+" to "+destinationDir+":");
                System.out.println("---------------");
                finder.find(startingPoint);
                System.out.println("\nDone.\n");
            }   
        });
    }

    /**
     * This command archives the image files under a starting directory (startingPoint) 
     * into the archive located under destinationDir.  The directory-structure of the 
     * source is abandoned, and all files are archived into a single directory (newDirName).
     * 
     * @param startingPoint
     * @param newDirName
     * @param destinationDir
     * @return
     */
    public static JobResults archiveCard(final File startingPoint, 
                                         final String newDirName, 
                                         final File destinationDir)
    {
        log.msg(">>> Archive Card Starting at "+startingPoint+" => "+destinationDir+"..."+newDirName);
        
        return runJob(destinationDir, new DbJob()
        {
            public void runJob(ImageArchiveDB db, JobResults results) throws SQLException
            {
                makeNewJob(db);
                
                FileFind.FindVisitor dbVisitor = 
                    new DbAndCopyVisitor1(db, destinationDir, newDirName, results);

                FileFind finder = new FileFind(dbVisitor, new ImageFilter());

                System.out.println("Archive "+startingPoint+" to "+destinationDir+":");
                System.out.println("---------------");
                finder.find(startingPoint);
                System.out.println("\nDone.\n");
            }   
        });
    }

    public static JobResults validateDbAgainstFiles(final File dbRoot, final File filesRoot)
    {
        log.msg(">>> Validate DB in "+dbRoot+" against files in "+filesRoot);
        
        return runJob(dbRoot, new DbJob()
        {
            public void runJob(ImageArchiveDB db, JobResults errors) throws SQLException
            {
                File filesDir = makeFilesDirFrom(filesRoot);

                System.out.println("Validate DB entries in "+db+":");
                System.out.println("---------------");
                
                Map<String, String> entries = db.getEntries();
                System.out.println("Done querying DB.");
                
                for (String dbMd5sum : entries.keySet())
                {
                    String dbFilename = entries.get(dbMd5sum);
                    File file = new File(filesDir, dbFilename);
                    if(file.exists())
                    {
                        String fileMd5Sum = calcMD5For(file);
                        if(!dbMd5sum.equals(fileMd5Sum))
                        {
                            errors.addError(new ImageArchiveDB.InvalidEntry(dbMd5sum, dbFilename, null, "disk MD5 doesn't match DB"));
                        }
                    }
                    else
                    {
                        errors.addError(new ImageArchiveDB.InvalidEntry(dbMd5sum, dbFilename, null, "file not found on disk"));
                    }
                }

                //printErrors(errors);
                System.out.println("\nJob Complete.\n");
            }   
        });
    }
    
    static JobResults fixBadBackup(final File srcArchiveDir, final File dstArchiveDir)
    {
        log.msg(">>> Fix: "+srcArchiveDir+" and "+dstArchiveDir);
        System.out.println("Fix "+srcArchiveDir+" and "+dstArchiveDir+":");
        System.out.println("---------------");
        
        final File srcArchiveFilesDir = makeFilesDirFrom(srcArchiveDir);
        final File dstArchiveFilesDir = makeFilesDirFrom(dstArchiveDir);
        
        return ArchiverBase.runJob(srcArchiveDir, new DbJob(){
            public void runJob(final ImageArchiveDB srcDb, JobResults results) throws SQLException, ClassNotFoundException
            {
                ArchiverBase.runJobNoTraps(dstArchiveDir, results, new DbJob(){
                    public void runJob(ImageArchiveDB dstDb, JobResults results) throws SQLException, ClassNotFoundException
                    {
                        System.out.println("Get DB entries in "+srcDb+":");
                        System.out.println("---------------");
                        
                        Map<String, String> entries = srcDb.getEntries();
                        System.out.println("Done querying DB.");
                        
                        for (String dbMd5sum : entries.keySet())
                        {
                            String dbFilename = entries.get(dbMd5sum);
                            File dstFile = new File(dstArchiveFilesDir, dbFilename);
                            if(dstFile.exists()) // (i.e. good backup)
                            {
                                String fileMd5Sum = calcMD5For(dstFile);
                                if(!dbMd5sum.equals(fileMd5Sum))
                                {
                                    results.addError(
                                            new ImageArchiveDB.InvalidEntry(dbMd5sum, 
                                                                            dbFilename, 
                                                                            null, 
                                                                            "disk MD5 doesn't match DB for "+dstFile));
                                }
                            }
                            else // dstFile does not exist (i.e. no backup made)
                            {
                                File srcFile = new File(srcArchiveFilesDir, dbFilename);
                                if(srcFile.exists())
                                {
                                    String fileMd5Sum = calcMD5For(srcFile);
                                    if(dbMd5sum.equals(fileMd5Sum))
                                    {
                                        // This is the "main event" of this function!
                                        // If we get here, the backup-tier doens't contain the file, 
                                        // but the primary-tier does.
                                        // Our job is:
                                        //  1) Mark the primary-tier's DB entry as "not backed up"
                                        //  2) Remove the backup-tier's DB entry:
                                        srcDb.setAsNotBackedUp(dbMd5sum);
                                        dstDb.delete(dbMd5sum);
                                    }
                                    else
                                    {
                                        results.addError(
                                                new ImageArchiveDB.InvalidEntry(dbMd5sum, 
                                                                                dbFilename, 
                                                                                null, 
                                                                                "disk MD5 doesn't match DB for "+srcFile));
                                    }
                                    
                                }
                                else
                                {
                                    results.addError(
                                            new ImageArchiveDB.InvalidEntry(dbMd5sum, 
                                                                            dbFilename, 
                                                                            null, 
                                                                            "File does not exist "+srcFile));
                                }
                            }
                        }
                    }
                });
            }
        });
    }

    public static JobResults findForFilename(final File srcArchiveDir, final String filenameFragment)
    {
        log.msg(">>> Query DB "+srcArchiveDir+" for filenames containing "+filenameFragment);

        return runJob(srcArchiveDir, new DbJob()
        {
            public void runJob(ImageArchiveDB db, JobResults results) throws SQLException
            {
                Map<String, String> matches = db.findForFilenameContaining(filenameFragment);
                
                for( Entry<String, String>entry : matches.entrySet())
                {
                    String relPath = entry.getValue();
                    String md5Sum = entry.getKey();
                    results.addResult(md5Sum, relPath);
                }
            }   
        });
    }

    public static JobResults findForMD5Sum(final File srcArchiveDir, final String md5Sum)
    {
        log.msg(">>> Query DB "+srcArchiveDir+" for file with MD5 "+md5Sum);

        return runJob(srcArchiveDir, new DbJob()
        {
            public void runJob(ImageArchiveDB db, JobResults results) throws SQLException
            {
                String relPath = db.alreadyExists(md5Sum);
                
                if(relPath!=null)
                {
                    results.addResult(md5Sum, relPath);
                }
            }   
        });
    }

    public static JobResults fixDbForFile(final File archiveRootDir, final String relativeFilename)
    {
        log.msg(">>> Fix DB in "+archiveRootDir+" for filename "+relativeFilename);

        return runJob(archiveRootDir, new DbJob()
        {
            public void runJob(ImageArchiveDB db, JobResults results) throws SQLException
            {
                final File srcArchiveFilesDir = makeFilesDirFrom(archiveRootDir);
                final File fileToFix = new File(srcArchiveFilesDir, relativeFilename);
                if(fileToFix.exists())
                {
                    String md5sum = calcMD5For(fileToFix);
                    String currentRelPath = db.alreadyExists(md5sum);
                    if(currentRelPath != null)
                    {
                        db.update(md5sum, relativeFilename);
                    }
                    else
                    {
                        results.addError(new InvalidEntry(md5sum, relativeFilename, null, "No DB Entry Found for MD5"));
                    }
                }
                else
                {
                    results.addError(new InvalidEntry(null, relativeFilename, null, "File Not Found"));
                }
            }   
        });
    }
}
