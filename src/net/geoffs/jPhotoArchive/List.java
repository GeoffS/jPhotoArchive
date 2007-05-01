package net.geoffs.jPhotoArchive;
import java.io.File;
import java.sql.SQLException;

public class List extends ArchiverBase
{
    public static void main(String[] args)
    {
        final File destinationDir = new File(args[0]);

        runJob(destinationDir, new DbJob()
        {
            public void runJob(ImageArchiveDB db, JobResults results) throws SQLException
            {
                System.out.println("Listing "+destinationDir+":");
                System.out.println("---------------");
                db.dump();
                System.out.println("\nDone.\n");
            } 
        });
    }
}
