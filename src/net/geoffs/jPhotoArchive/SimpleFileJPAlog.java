package net.geoffs.jPhotoArchive;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import net.geoffs.jPhotoArchive.ImageArchiveDB.InvalidEntry;

public class SimpleFileJPAlog implements JPAlog
{
    private static final SimpleDateFormat formater = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
    private final File logFile;
    private final PrintWriter out;
    
    public SimpleFileJPAlog(File logsDir) throws IOException
    {
        logFile = new File( logsDir, makeUniqueLogfileName());
        logFile.getParentFile().mkdirs();
        out = new PrintWriter(new FileWriter(logFile), true);
        out.println("jPhotoArchive Logger initialized "+new Date());
    }
    
    public void close()
    {
       out.println("jPhotoArchive Logger closing "+new Date());
       out.close();
    }
    
    private String makeUniqueLogfileName()
    {
        return formater.format(new Date())+".log";
    }

    public void commandLine(String[] args)
    {
        out.print("Command line: ");
        for (int i = 0; i < args.length; i++)
        {
            out.print(args[i]+" ");
        }
        out.println();
    }

    public void msg(String msg)
    {
        out.println(msg);
    }

    public void exit(String msg)
    {
        out.println("Program exiting at "+new Date());
        out.println(msg);
    }

    public void warning(String msg)
    {
        out.println("WARNING: "+msg);
    }
    
    public void error(String msg)
    {
        out.println("ERROR: "+msg);
    }

    public void error(String msg, Exception e)
    {
        printSeparator();
        error(msg);
        e.printStackTrace(out);
        printSeparator();
    }

    private void printSeparator()
    {
        out.println("==============================================");
    }

    public void dbStmt(ImageArchiveDB archiveDB, PreparedStatement insertStmt)
    {
        msg(archiveDB.toString()+": "+insertStmt.toString());
    }

    public void errors(List<InvalidEntry> errors)
    {
        printSeparator();
        out.println(errors.size()+" Errors were found:");
        for (InvalidEntry err : errors)
        {
            out.print(err.queryRelPath+"["+err.md5Sum+"]");
            if(err.resultRelPath!=null)
            {
                out.print(" -> "+err.resultRelPath);
            }
            out.println(": "+err.reason);
        }
        printSeparator();
    }

    @Override
    public String TimeStamp()
    {
        final String formattedDate = new SimpleDateFormat().format(new Date());
        msg(formattedDate);
        return formattedDate;
    }
}
