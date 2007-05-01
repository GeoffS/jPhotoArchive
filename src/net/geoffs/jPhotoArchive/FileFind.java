package net.geoffs.jPhotoArchive;
import java.io.File;
import java.io.FileFilter;


public class FileFind
{   
    private final FindVisitor visitor;
    private final FileFilter filter;
    
    public FileFind(FindVisitor visitor, FileFilter filter)
    {
        this.visitor = visitor;
        this.filter  = filter;
    }
    
    public void find(File current)
    {
        File rootPath = current.getParentFile();
        String relativePath = current.getName();
        find(rootPath, relativePath);
    }
    
    private void find( final File rootPath, final String relativePath )
    {
        File current = new File(rootPath, relativePath);
        if(current.isFile())
        {
            if(filter.accept(current))
            {
                visitor.processFile(rootPath, relativePath);
            }
        }
        else if(current.isDirectory())
        {
            //visitor.preprocessDir(current);
            String dirContents[] = current.list();
            for (int i = 0; i < dirContents.length; i++)
            {
                find(rootPath, new File( new File(relativePath), dirContents[i]).getPath());
            }
            visitor.postProcessDir(current);
        }
    }

    public static interface FindVisitor
    {
        //void preprocessDir(File dir);
        void processFile(File rootPath, String relativePath);
        void postProcessDir(File dir);
    }
    
    public static abstract class FileOnlyVisitor implements FindVisitor
    {
        //public void preprocessDir(File dir){}
        //public void processFile(File rootPath, String relativePath){}
        public void postProcessDir(File dir){}
    }
    
    public static class PrintVisitor extends FileFind.FileOnlyVisitor
    {
        public void processFile(File rootPath, String relativePath)
        {
            System.out.println(rootPath+"..."+relativePath);
        }
    }
}
