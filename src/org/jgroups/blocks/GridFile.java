package org.jgroups.blocks;

import org.jgroups.util.Streamable;
import org.jgroups.util.Util;

import java.io.File;
import java.io.IOException;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.net.URI;
import java.util.*;

/**
 * Subclass of File to iterate through directories and files in a grid
 * @author Bela Ban
 * @version $Id: GridFile.java,v 1.3 2009/12/22 16:29:06 belaban Exp $
 */
public class GridFile extends File {
    private static final long serialVersionUID=-6729548421029004260L;
    private final ReplCache<String,Metadata> cache;
    private final String name;
    private final int chunk_size;

    public GridFile(String pathname, ReplCache<String, Metadata> cache, int chunk_size) {
        super(pathname);
        this.name=trim(pathname);
        this.cache=cache;
        this.chunk_size=chunk_size;
    }

    public GridFile(String parent, String child, ReplCache<String, Metadata> cache, int chunk_size) {
        super(parent, child);
        this.name=trim(parent + File.separator + child);
        this.cache=cache;
        this.chunk_size=chunk_size;
    }

    public GridFile(File parent, String child, ReplCache<String, Metadata> cache, int chunk_size) {
        super(parent, child);
        this.name=trim(parent.getAbsolutePath() + File.separator + child);
        this.cache=cache;
        this.chunk_size=chunk_size;
    }

    public GridFile(URI uri, ReplCache<String, Metadata> cache, int chunk_size) {
        super(uri);
        this.name=trim(getAbsolutePath());
        this.cache=cache;
        this.chunk_size=chunk_size;
    }

    public boolean createNewFile() throws IOException {
        if(exists())
            return false;
        if(!checkParentDirs(name, false))
            return false;
        cache.put(name, new Metadata(0, System.currentTimeMillis(), chunk_size, Metadata.FILE), (short)-1, 0);
        return true;
    }

    public boolean mkdir() {
        try {
            boolean parents_exist=checkParentDirs(name, false);
            if(!parents_exist)
                return false;
            cache.put(name, new Metadata(0, System.currentTimeMillis(), chunk_size, Metadata.DIR), (short)-1, 0);
            return true;
        }
        catch(IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean mkdirs() {
        try {
            boolean parents_exist=checkParentDirs(name, true);
            if(!parents_exist)
                return false;
            cache.put(name, new Metadata(0, System.currentTimeMillis(), chunk_size, Metadata.DIR), (short)-1, 0);
            return true;
        }
        catch(IOException e) {
            return false;
        }
    }

    public boolean exists() {
        return cache.get(name) != null;
    }

    public String[] list() {
        Cache<String, ReplCache.Value<Metadata>> internal_cache=cache.getL2Cache();
        Set<String> keys=internal_cache.getInternalMap().keySet();
        if(keys == null)
            return null;
        Collection<String> list=new ArrayList<String>(keys.size());
        for(String str: keys) {
            if(isChildOf(name, str))
                list.add(str);
            else
                System.err.println("omitting " + str);
        }
        String[] retval=new String[list.size()];
        int index=0;
        for(String tmp: list)
            retval[index++]=tmp;
        return retval;
    }

    /**
     * Verifies whether child is a child (dir or file) of parent
     * @param parent
     * @param child
     * @return True if child is a child, false otherwise
     */
    protected static boolean isChildOf(String parent, String child) {
        if(parent == null || child == null)
            return false;
        if(!child.startsWith(parent))
            return false;
        int from=parent.length();
        String[] comps=components(child, from);
        return comps == null || comps.length <= 1;
    }

    /**
     * Checks whether the parent directories are present (and are directories). If create_if_absent is true,
     * creates missing dirs
     * @param path
     * @param create_if_absent
     * @return
     */
    private boolean checkParentDirs(String path, boolean create_if_absent) throws IOException {
        String[] components=components(path, 0);
        if(components == null)
            return false;
        if(components.length == 1) // no parent directories to create, e.g. "data.txt"
            return true;

        StringBuilder sb=new StringBuilder(File.separator);
        boolean first=true;

        for(int i=0; i < components.length-1; i++) {
            String tmp=components[i];
            if(first)
                first=false;
            else
                sb.append(File.separator);
            sb.append(tmp);
            String comp=sb.toString();
            if(exists(comp)) {
                if(isFile(comp))
                    throw new IOException("cannot create " + path + " as component " + comp + " is a file");
            }
            else {
                if(create_if_absent)
                    cache.put(comp, new Metadata(0, System.currentTimeMillis(), chunk_size, Metadata.DIR), (short)-1, 0);
                else
                    return false;
            }
        }
        return true;
    }

    private static String[] components(String path, int from) {
        if(path == null)
            return null;
        path=path.trim();
        int index=path.indexOf(File.separator, from);
        if(index == from)
            path=path.substring(from+1);
        else if(index == -1)
            return null;
        return path.split(File.separator);
    }

    protected static String trim(String str) {
        if(str == null) return null;
        str=str.trim();
        while(str.lastIndexOf(separator) == str.length()-1)
            str=str.substring(0, str.length()-1);
        return str;
    }

    private boolean exists(String key) {
        return cache.get(key) != null;
    }

    private boolean isFile(String key) {
        Metadata val=cache.get(key);
        return val.isFile();
    }



    
    public static class Metadata implements Streamable {
        public static final byte FILE = 1 << 0;
        public static final byte DIR  = 1 << 1;

        private int  length =0;
        private long modification_time=0;
        private int  chunk_size=0;
        private byte flags=0;


        public Metadata() {
        }

        public Metadata(int length, long modification_time, int chunk_size, byte flags) {
            this.length=length;
            this.modification_time=modification_time;
            this.chunk_size=chunk_size;
            this.flags=flags;
        }

        public int getLength() {
            return length;
        }

        public void setLength(int length) {
            this.length=length;
        }

        public long getModificationTime() {
            return modification_time;
        }

        public void setModificationTime(long modification_time) {
            this.modification_time=modification_time;
        }

        public int getChunkSize() {
            return chunk_size;
        }

        public boolean isFile() {
            return Util.isFlagSet(flags, FILE);
        }

        public boolean isDirectory() {
            return Util.isFlagSet(flags, DIR);
        }

        public String toString() {
            boolean is_file=Util.isFlagSet(flags, FILE);
            StringBuilder sb=new StringBuilder();
            sb.append(getType());
            if(is_file)
                sb.append(", len=" + Util.printBytes(length) + ", chunk_size=" + chunk_size);
            sb.append(", mod_time=" + new Date(modification_time));
            return sb.toString();
        }

        public void writeTo(DataOutputStream out) throws IOException {
            out.writeInt(length);
            out.writeLong(modification_time);
            out.writeInt(chunk_size);
            out.writeByte(flags);
        }

        public void readFrom(DataInputStream in) throws IOException, IllegalAccessException, InstantiationException {
            length=in.readInt();
            modification_time=in.readLong();
            chunk_size=in.readInt();
            flags=in.readByte();
        }

        private String getType() {
            if(Util.isFlagSet(flags, FILE))
                return "file";
            if(Util.isFlagSet(flags, DIR))
                return "dir";
            return "n/a";
        }
    }
}