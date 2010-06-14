package net.geoffs.jPhotoArchive;
import java.sql.PreparedStatement;
import java.util.List;


public interface JPAlog
{

    void commandLine(String[] args);

    void msg(String msg);

    void exit(String string);

    void warning(String msg);

    void error(String msg, Exception e);

    void error(String msg);

    void dbStmt(ImageArchiveDB archiveDB, PreparedStatement insertStmt);

    void errors(List<ImageArchiveDB.InvalidEntry> errors);

    void close();

    String TimeStamp();

}
