package net.geoffs.jPhotoArchive;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.geoffs.jPhotoArchive.ArchiverBase.JobResults;

public class ImageArchiveDB
{
    public static final String PHOTOS_TABLE = "photos";
    public static final String JOBS_TABLE = "jobs";
    
    public static final String FILENAME_COLUMN = "filename";
    public static final String MD5SUM_COLUMN = "md5sum";
    public static final String ARCHIVE_TIME_COLUMN = "archived_on";
    public static final String ARCHIVED_TO_NEXT_TIER_COLUMN = "next_tier";
    
    public static final String JOB_NUMBER_COLUMN = "run_number";
    public static final String JOB_TIME = "run_time";
    
    public void dump(JobResults results) throws SQLException
    {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT * from "+PHOTOS_TABLE+";");
        for (; rs.next(); ) {
            String md5hex    = rs.getString(MD5SUM_COLUMN);
            String filename  = rs.getString(FILENAME_COLUMN);
            int jobNumber = rs.getInt(JOB_NUMBER_COLUMN);
            Timestamp archivedOn = rs.getTimestamp(ARCHIVE_TIME_COLUMN);
            boolean sentToNextTier = rs.getBoolean(ARCHIVED_TO_NEXT_TIER_COLUMN);

            String info = "("+jobNumber+", "+archivedOn+", "+sentToNextTier+")";
            results.addResult(new Result(md5hex, filename, info));
            
            System.out.println(md5hex+" - "+filename+" "+info);
        }
        stmt.close();
    }
    
    public Map<String, String> getEntries() throws SQLException
    {
        final Map<String, String> entries = new HashMap<String, String>();
        
//        Statement stmt = conn.createStatement();
//        ResultSet rs = stmt.executeQuery("SELECT * from "+PHOTOS_TABLE+";");
        mapQuery(selectStar, new ResultsProcessor(){
            public void process(ResultSet rs) throws SQLException
            {
                String md5hex    = rs.getString(MD5SUM_COLUMN);
                String filename  = rs.getString(FILENAME_COLUMN);
                entries.put(md5hex, filename);
            }
        });
//        for (; rs.next(); ) {
//            String md5hex    = rs.getString(MD5SUM_COLUMN);
//            String filename  = rs.getString(FILENAME_COLUMN);
//            entries.put(md5hex, filename);
//        }
//        stmt.close();
        return entries;
    }
    
    private static void mapQuery(PreparedStatement query, ResultsProcessor proc) throws SQLException
    {
        ResultSet rs = query.executeQuery();
        for (; rs.next(); ) {
            proc.process(rs);
        }
        query.close();
    }
    
    private static interface ResultsProcessor
    {
        void process(ResultSet rs) throws SQLException;
    }

    public String alreadyExists(String md5sum) throws SQLException
    {
        checkExistanceStmt.clearParameters();
        checkExistanceStmt.setString(1, md5sum);
        ResultSet rs = checkExistanceStmt.executeQuery();
        
        if(rs.next() == false) return null;
        
        String existingFile = rs.getString(1);
        
        if(rs.next())
        {
            throw new RuntimeException("More than one row returned by query for: "+md5sum);
        }
        
        return existingFile;
    }

    public void insert(String md5hex, String filename) throws SQLException
    {
        insertStmt.clearParameters();
        insertStmt.setString(1, md5hex);
        insertStmt.setString(2, filename);
        insertStmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
        log.dbStmt(this, insertStmt);
        insertStmt.executeUpdate();
    }
    
    public int newJob() throws SQLException
    {
        newJobStmt.clearParameters();
        newJobStmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
        log.dbStmt(this, newJobStmt);
        newJobStmt.executeUpdate();
        
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("CALL IDENTITY();");
        rs.next();
        return rs.getInt(1);
    }

    public static void createDB(File newDbDir, JPAlog log) throws SQLException, ClassNotFoundException
    {
        log.msg("Create new archive DB in "+newDbDir);
        // Make the dir for the database file:
        newDbDir.mkdirs();
        
        Connection newDbConnection = null;
        try
        {
        newDbConnection = openConnection(newDbDir);
        
        runStatement(newDbConnection,
                "CREATE TABLE "+JOBS_TABLE+" ("+
                JOB_NUMBER_COLUMN+" INTEGER IDENTITY, "+
                JOB_TIME+" TIMESTAMP NOT NULL)",
                log);
        
        runStatement(newDbConnection,
                "CREATE TABLE "+PHOTOS_TABLE+" ("+
                MD5SUM_COLUMN+" CHAR(32) primary key, "+
                FILENAME_COLUMN+" VARCHAR(256) NOT NULL, "+
                ARCHIVE_TIME_COLUMN+" TIMESTAMP NOT NULL, "+
                JOB_NUMBER_COLUMN+" INTEGER NOT NULL, "+
                ARCHIVED_TO_NEXT_TIER_COLUMN+" BOOLEAN NOT NULL, "+
                "FOREIGN KEY ("+JOB_NUMBER_COLUMN+") REFERENCES "+JOBS_TABLE+"("+JOB_NUMBER_COLUMN+"))",
                log);
        }
        finally
        {
            shutdown(newDbConnection, log);
        }
    }

    private static void runStatement(Connection newDbConnection,
                                     String statment,
                                     JPAlog log) 
    throws SQLException
    {
        log.msg(statment);
        Statement stmt = newDbConnection.createStatement();
        stmt.execute(statment);
        stmt.close();
    }
    
    public static class Result
    {
        public final String md5Sum;
        public final String resultRelPath;
        public final String reason;
        
        public Result(final String md5Sum, 
                      final String resultRelPath, 
                      final String reason)
        {
            this.md5Sum = md5Sum;
            this.resultRelPath = resultRelPath;
            this.reason = reason;
        }

        public Result(String md5Sum, String resultRelPath)
        {
            this(md5Sum, resultRelPath, null);
        }
    }
    
    public static class InvalidEntry extends Result
    {
        public final String queryRelPath;
        
        public InvalidEntry( final String md5Sum, 
                             final String queryRelPath, 
                             final String resultRelPath, 
                             final String reason)
        {
            super(md5Sum, resultRelPath, reason);
            
            this.queryRelPath = queryRelPath;
        }
        
        public InvalidEntry(final String msg)
        {
            this(null, null, null, msg);
        }
        
        public InvalidEntry(Exception e)
        {
            this(null, null, null, "Job failed: "+e.toString());
        }
    }
    
    public void validate(String md5sum, String fullDiskPath, List<InvalidEntry> collector) throws SQLException
    {
        String relDiskPath = stripArchiveDir(fullDiskPath);
        String dbPath = alreadyExists(md5sum);
        
        if(dbPath==null)
        {
            collector.add(new InvalidEntry(md5sum, relDiskPath, dbPath, "not found"));
            //System.out.println("\nError: "+relDiskPath+" ("+md5sum+") not found.");
            return;
        }
        
        if(!relDiskPath.equals(dbPath))
        {
            collector.add(new InvalidEntry(md5sum, relDiskPath, dbPath, "does not match DB entry"));
            //System.out.println("\nError: "+relDiskPath+" ("+md5sum+") does not match DB entry: "+dbPath);
            return;
        }
    }

    //============================================================================
    // Windows:
    private static final String FILE_SEPARATOR_REGEX = "\\\\";
    
    private String stripArchiveDir(String fullDiskPath)
    {
        String[] chunks = fullDiskPath.split(FILE_SEPARATOR_REGEX);
        StringBuffer buf = new StringBuffer();
        buf.append(chunks[2]);
        for (int i = 3; i < chunks.length; i++)
        {
            buf.append(File.separator);
            buf.append(chunks[i]);
        }
        return buf.toString();
    }

    private final Connection conn;
    private final PreparedStatement insertStmt;
    private final PreparedStatement checkExistanceStmt;
    private final PreparedStatement newJobStmt;
    private final PreparedStatement updateBackedUpStmt;
    private final PreparedStatement backedupStmt;
    private final PreparedStatement deleteStmt;
    private final PreparedStatement updatePathStmt;
    //private final PreparedStatement filenameQuery;
    private final PreparedStatement selectStar;
    
    private final String name;
    private final JPAlog log;
    
    public ImageArchiveDB(File dbDir, JPAlog log) throws ClassNotFoundException, SQLException
    {
        this.name = dbDir.getParent();
        
        this.log = log;
        
        this.conn = openConnection(dbDir);
        
        this.checkExistanceStmt = conn.prepareStatement(
                "select "+FILENAME_COLUMN+" from "+PHOTOS_TABLE+" where "+MD5SUM_COLUMN+" = ?;");
        
        this.insertStmt = conn.prepareStatement(
                "INSERT INTO "+PHOTOS_TABLE+"("+
                MD5SUM_COLUMN+", "+
                FILENAME_COLUMN+", "+
                ARCHIVE_TIME_COLUMN+", "+
                ARCHIVED_TO_NEXT_TIER_COLUMN+", "+
                JOB_NUMBER_COLUMN+") "+
                "VALUES(?,?,?,FALSE,IDENTITY());");
        
        this.newJobStmt = conn.prepareStatement(
                "INSERT INTO "+JOBS_TABLE+"("+
                JOB_TIME+") "+
                "VALUES(?);");
        
        this.updateBackedUpStmt = conn.prepareStatement(
                "UPDATE "+PHOTOS_TABLE+
                " SET "+ARCHIVED_TO_NEXT_TIER_COLUMN+" = ?"+
                " where "+MD5SUM_COLUMN+" = ? ;");
        
        this.backedupStmt = conn.prepareStatement(
                "SELECT * from "+PHOTOS_TABLE+" where "+ARCHIVED_TO_NEXT_TIER_COLUMN+" = ?;");
        
        this.deleteStmt = conn.prepareStatement(
                "DELETE from "+PHOTOS_TABLE+" where "+MD5SUM_COLUMN+" = ?;");
        
        this.updatePathStmt = conn.prepareStatement(
                "UPDATE "+PHOTOS_TABLE+
                " SET "+FILENAME_COLUMN+" = ?"+
                " where "+MD5SUM_COLUMN+" = ? ;");
        
//        this.filenameQuery = conn.prepareStatement(
//                "SELECT * from "+PHOTOS_TABLE+" where "+FILENAME_COLUMN+" LIKE ?;");
        
        this.selectStar = conn.prepareStatement(
                "SELECT * from "+PHOTOS_TABLE+";");
    }

//    private RuntimeException makeFatalDbError(Exception e)
//    {
//        //e.printStackTrace();
//        return new RuntimeException("Can't initialize database", e);
//    }

    private static Connection openConnection(File dbDir) throws ClassNotFoundException, SQLException
    {
        File db = new File(dbDir, "photoDB");
        
        // Load the HSQL Database Engine JDBC driver
        // hsqldb.jar should be in the class path or made part of the current jar
        Class.forName("org.hsqldb.jdbcDriver");
        
        
        //String dbUrl = "jdbc:hsqldb:mem:aname";
        String dbUrl = "jdbc:hsqldb:file:"+db.getPath();
        
        //System.out.println("Opening db URL: "+dbUrl);
        return DriverManager.getConnection(dbUrl, "sa", "");
    }
    
    public void shutdown() throws SQLException 
    {
        shutdown(this.conn, log);
    }
        
    public static void shutdown(Connection conn, JPAlog log) throws SQLException 
    {
        log.msg("Stopping the DB...");
        System.out.print("Stopping the DB...");
        if(conn==null) 
        {
            log.warning("No DB Connection, nothing to close.");
            System.out.println("No DB Connection, nothing to close.");
            return;
        }
        
        System.out.print("shutdown...");
        log.msg("Execute SHUTDOWN statment");
        Statement st = conn.createStatement();
        // db writes out to files and performs clean shuts down
        // otherwise there will be an unclean shutdown
        // when program ends
        st.execute("SHUTDOWN");
        System.out.print("close...");
        conn.close();    // if there are no other open connection
        System.out.println();
    }

    public Map<String, String> getUnbackedupEntries() throws SQLException
    {
        return getEntriesWithBackupStatus(false);
    }

    public void setAsBackedUp(String md5Sum) throws SQLException
    {
        setBackupStatus(md5Sum, true);
//        updateBackedUpStmt.clearParameters();
//        updateBackedUpStmt.setString(1, md5Sum);
//        updateBackedUpStmt.setBoolean(1, true);
//        log.dbStmt(this, updateBackedUpStmt);
//        updateBackedUpStmt.executeUpdate();
    }
    
    public void setAsNotBackedUp(String md5Sum) throws SQLException
    {
        setBackupStatus(md5Sum, false);
    }
    
    public void setBackupStatus(String md5Sum, boolean status) throws SQLException
    {
        updateBackedUpStmt.clearParameters();
        updateBackedUpStmt.setString(2, md5Sum);
        updateBackedUpStmt.setBoolean(1, status);
        log.dbStmt(this, updateBackedUpStmt);
        updateBackedUpStmt.executeUpdate();
    }

    public Map<String, String> getBackedupEntries() throws SQLException
    {
        return getEntriesWithBackupStatus(true);
    }

    public Map<String, String> getEntriesWithBackupStatus(boolean backupStatus) throws SQLException
    {
        backedupStmt.clearParameters();
        backedupStmt.setBoolean(1, backupStatus);
        
        return runQuery(backedupStmt);
    }
    
    private static Map<String, String> runQuery(PreparedStatement query) throws SQLException
    {
        final Map<String, String> entries = new HashMap<String, String>();
        
        mapQuery(query, new ResultsProcessor(){
          public void process(ResultSet rs) throws SQLException
          {
              entries.put(rs.getString(MD5SUM_COLUMN), rs.getString(FILENAME_COLUMN));
          }
      });
        return entries;
    }

    @Override
    public String toString()
    {
        return name;
    }

    public void delete(String dbMd5sum) throws SQLException
    {
        deleteStmt.clearParameters();
        deleteStmt.setString(1, dbMd5sum);
        log.dbStmt(this, deleteStmt);
        int deletes = deleteStmt.executeUpdate();
        if(deletes != 1)
        {
            String msg = "Wrong number of records deleted, should have been 1, was "+deletes;
            log.error(msg);
            throw new SQLException(msg);
        }
    }

    public void update(String md5sum, String newPath) throws SQLException
    {
        updatePathStmt.clearParameters();
        updatePathStmt.setString(1, newPath);
        updatePathStmt.setString(2, md5sum);
        log.dbStmt(this, updatePathStmt);
        int updates = updatePathStmt.executeUpdate();
        if(updates != 1)
        {
            String msg = "Wrong number of records update, should have been 1, was "+updates;
            log.error(msg);
            throw new SQLException(msg);
        }
    }

    public Map<String, String> findForFilenameContaining(final String filenameFragment) throws SQLException
    {
        final Map<String, String> entries = new HashMap<String, String>();
        
        mapQuery(selectStar, new ResultsProcessor(){
          public void process(ResultSet rs) throws SQLException
          {
              String filename = rs.getString(FILENAME_COLUMN);
              if(filename.contains(filenameFragment))
              {
                  String md5hex = rs.getString(MD5SUM_COLUMN);
                  entries.put(md5hex, filename);
              }
          }
      });
        
        return entries;
    }
}
