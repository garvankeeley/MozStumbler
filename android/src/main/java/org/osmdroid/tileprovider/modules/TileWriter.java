package org.osmdroid.tileprovider.modules;

import java.io.File;

import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.BufferedOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import org.osmdroid.tileprovider.MapTile;
import org.osmdroid.tileprovider.constants.OpenStreetMapTileProviderConstants;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link IFilesystemCache}. It writes tiles to the file system cache. If the
 * cache exceeds 600 Mb then it will be trimmed to 500 Mb.
 *
 * @author Neil Boyd
 *
 */
// @TODO: vng IFilesystemCache is only implemented by this one class.
// We should just tighten up the public interface of TileWriter and
// drop IFilesystemCache entirely
//
// This is also not a cache at all. It's the only place in osmdroid
// which does any IO to storage.
//
// The classs has also been extended to do read/write of Etags, so
// it's not even just a Writer anymore.
public class TileWriter implements IFilesystemCache, OpenStreetMapTileProviderConstants {

    // ===========================================================
    // Constants
    // ===========================================================

    private static final Logger logger = LoggerFactory.getLogger(TileWriter.class);

    // ===========================================================
    // Fields
    // ===========================================================

    /** amount of disk space used by tile cache **/
    private static long mUsedCacheSpace;

    // ===========================================================
    // Constructors
    // ===========================================================

    public TileWriter() {
        shrinkCacheInBackground();
    }

    private void shrinkCacheInBackground() {
        // @TODO: vng put a static synchronized guard here so that the
        // background shrink can only happen in 1 background thread at
        // a time.
        //
        // We can then invoke the shrink method as much as we want
        // without worrying about spinning up too many background
        // threads.
        final Thread t = new Thread() {
            @Override
            public void run() {
                mUsedCacheSpace = 0; // because it's static
                calculateDirectorySize(TILE_PATH_BASE);
                if (mUsedCacheSpace > TILE_MAX_CACHE_SIZE_BYTES) {
                    cutCurrentCache();
                }
            }
        };
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    /**
     * Get the amount of disk space used by the tile cache. This will initially be zero since the
     * used space is calculated in the background.
     *
     * @return size in bytes
     */
    public static long getUsedCacheSpace() {
        return mUsedCacheSpace;
    }

    // ===========================================================
    // Methods from SuperClass/Interfaces
    // ===========================================================

    public String readEtag(final ITileSource pTileSource, final MapTile pTile) {
        File file;
        File etagFile;
        BufferedInputStream inputStream;

        String tileFilename = pTileSource.getTileRelativeFilenameString(pTile);
        etagFile = new File(TILE_PATH_BASE,
                tileFilename + ".etag");

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(etagFile.getPath());
            inputStream = new BufferedInputStream(fis);
            byte[] contents = new byte[1024];

            int bytesRead=0;
            String strFileContents = ""; 
            while((bytesRead = inputStream.read(contents)) != -1){ 
                strFileContents = new String(contents, 0, bytesRead);
            }
            return strFileContents;
        } catch (IOException ioEx) {
            logger.error("Failed to read etag file: ["+etagFile.getPath()+"]", ioEx);
        } finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } catch (IOException ioEx) {
                logger.error("osmdroid: error closing etag inputstream", ioEx);
            }
        }
        logger.error("osmdroid: error loading etag");
        return null;
    }


    @Override
    public boolean saveFile(final ITileSource pTileSource, final MapTile pTile,
            final InputStream pStream, String etag) {

        File file;
        File etagFile;
        BufferedOutputStream outputStream;

        File parent;

        if (etag != null) {
            String tileFilename = pTileSource.getTileRelativeFilenameString(pTile);
            etagFile = new File(TILE_PATH_BASE,
                                tileFilename + ".etag");

            parent = etagFile.getParentFile();

            if (!parent.exists() && !createFolderAndCheckIfExists(parent)) {
                return false;
            }


            try {
                FileOutputStream fos = new FileOutputStream(etagFile.getPath());
                outputStream = new BufferedOutputStream(fos);
                outputStream.write(etag.getBytes(Charset.forName("UTF-8")));
                outputStream.flush();
                outputStream.close();
                logger.info("Wrote ["+ etag +"] file at: ["+etagFile.getPath()+"]");
            } catch (IOException ioEx) {
                logger.error("Failed to create etag file: ["+etagFile.getPath()+"]", ioEx);
            }
        }

        file = new File(TILE_PATH_BASE, 
                    pTileSource.getTileRelativeFilenameString(pTile) + TILE_PATH_EXTENSION);

        parent = file.getParentFile();

        if (!parent.exists() && !createFolderAndCheckIfExists(parent)) {
            log("Can't create parent folder for actual PNG. parent ["+parent+"]");
            return false;
        }

        outputStream = null;
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(file.getPath()),
                    StreamUtils.IO_BUFFER_SIZE);
            final long length = StreamUtils.copy(pStream, outputStream);
            outputStream.flush();
            outputStream.close();
            log("Wrote final tile: ["+file.getPath()+"]");

            mUsedCacheSpace += length;
            if (mUsedCacheSpace > TILE_MAX_CACHE_SIZE_BYTES) {
                cutCurrentCache(); // TODO perhaps we should do this in the background
            }
        } catch (final IOException e) {
            logger.error("TileWriter: IOException while writing tile: ", e);
            return false;
        } finally {
            if (outputStream != null) {
                StreamUtils.closeStream(outputStream);
            }
        }
        return true;
    }

    // ===========================================================
    // Methods
    // ===========================================================

    private boolean createFolderAndCheckIfExists(final File pFile) {
        if (pFile.mkdirs()) {
            return true;
        }
        if (DEBUGMODE) {
            logger.debug("Failed to create " + pFile + " - wait and check again");
        }

        // if create failed, wait a bit in case another thread created it
        try {
            Thread.sleep(500);
        } catch (final InterruptedException ignore) {
        }
        // and then check again
        if (pFile.exists()) {
            if (DEBUGMODE) {
                logger.debug("Seems like another thread created " + pFile);
            }
            return true;
        } else {
            if (DEBUGMODE) {
                logger.debug("File still doesn't exist: " + pFile);
            }
            return false;
        }
    }

    private void calculateDirectorySize(final File pDirectory) {
        final File[] z = pDirectory.listFiles();
        if (z != null) {
            for (final File file : z) {
                if (file.isFile()) {
                    mUsedCacheSpace += file.length();
                }
                if (file.isDirectory() && !isSymbolicDirectoryLink(pDirectory, file)) {
                    calculateDirectorySize(file); // *** recurse ***
                }
            }
        }
    }

    /**
     * Checks to see if it appears that a directory is a symbolic link. It does this by comparing
     * the canonical path of the parent directory and the parent directory of the directory's
     * canonical path. If they are equal, then they come from the same true parent. If not, then
     * pDirectory is a symbolic link. If we get an exception, we err on the side of caution and
     * return "true" expecting the calculateDirectorySize to now skip further processing since
     * something went goofy.
     */
    private boolean isSymbolicDirectoryLink(final File pParentDirectory, final File pDirectory) {
        try {
            final String canonicalParentPath1 = pParentDirectory.getCanonicalPath();
            final String canonicalParentPath2 = pDirectory.getCanonicalFile().getParent();
            return !canonicalParentPath1.equals(canonicalParentPath2);
        } catch (final IOException e) {
            return true;
        } catch (final NoSuchElementException e) {
            // See: http://code.google.com/p/android/issues/detail?id=4961
            // See: http://code.google.com/p/android/issues/detail?id=5807
            return true;
        }

    }

    private List<File> getDirectoryFileList(final File aDirectory) {
        final List<File> files = new ArrayList<File>();

        final File[] z = aDirectory.listFiles();
        if (z != null) {
            for (final File file : z) {
                if (file.isFile()) {
                    files.add(file);
                }
                if (file.isDirectory()) {
                    files.addAll(getDirectoryFileList(file));
                }
            }
        }

        return files;
    }

    /**
     * If the cache size is greater than the max then trim it down to the trim level. This method is
     * synchronized so that only one thread can run it at a time.
     */
    private void cutCurrentCache() {

        synchronized (TILE_PATH_BASE) {

            if (mUsedCacheSpace > TILE_TRIM_CACHE_SIZE_BYTES) {

                logger.info("Trimming tile cache from " + mUsedCacheSpace + " to "
                        + TILE_TRIM_CACHE_SIZE_BYTES);

                final List<File> z = getDirectoryFileList(TILE_PATH_BASE);

                // order list by files day created from old to new
                final File[] files = z.toArray(new File[0]);
                Arrays.sort(files, new Comparator<File>() {
                    @Override
                    public int compare(final File f1, final File f2) {
                        return Long.valueOf(f1.lastModified()).compareTo(f2.lastModified());
                    }
                });

                for (final File file : files) {
                    if (mUsedCacheSpace <= TILE_TRIM_CACHE_SIZE_BYTES) {
                        break;
                    }

                    final long length = file.length();
                    if (file.delete()) {
                        mUsedCacheSpace -= length;
                    }
                }

                logger.info("Finished trimming tile cache");
            }
        }
    }
    private void log(String msg) {
        logger.info("TileWriter: " + msg);
    }

}
