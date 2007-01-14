/*
 * SIP Communicator, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.fileaccess;

import java.io.*;

import net.java.sip.communicator.service.configuration.*;
import net.java.sip.communicator.service.fileaccess.*;
import net.java.sip.communicator.util.*;

/**
 * Default FileAccessService implementation.
 *
 * @author Alexander Pelov
 */
public class FileAccessServiceImpl implements FileAccessService {

    /**
     * The logger for this class.
     */
    private static Logger logger = Logger
            .getLogger(FileAccessServiceImpl.class);

    /**
     * The file prefix for all temp files.
     */
    public static final String TEMP_FILE_PREFIX = "SIPCOMM";

    /**
     * The file suffix for all temp files.
     */
    public static final String TEMP_FILE_SUFFIX = "TEMP";

    /**
     * List of available configuration services.
     */
    private ConfigurationService configurationService = null;

    /**
     * An synchronization object.
     *
     * A lock should be obtained whenever the configuration service is accessed.
     */
    private Object syncRoot = new Object();

    /**
     * Set the configuration service.
     *
     * @param configurationService a currently active instance of the
     * configuration service
     */
    public void setConfigurationService(
            ConfigurationService configurationService)
    {
        synchronized (this.syncRoot)
        {
            this.configurationService = configurationService;
            logger.debug("New configuration service registered.");
        }
    }

    /**
     * Remove a configuration service.
     *
     * @param configurationService a currently active instance of the
     * configuration service
     */
    public void unsetConfigurationService(
            ConfigurationService configurationService)
    {
        synchronized (this.syncRoot)
        {
            if (this.configurationService == configurationService)
            {
                this.configurationService = null;
                logger.debug("Configuration service unregistered.");
            }
        }
    }

    /**
     * This method returns a created temporary file. After you close this file
     * it is not guaranteed that you will be able to open it again nor that it
     * will contain any information.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @return The created temporary file
     * @throws IOException
     *             If the file cannot be created
     */
    public File getTemporaryFile()
        throws IOException
    {
        File retVal = null;

        try
        {
            logger.logEntry();

            retVal = TempFileManager.createTempFile(TEMP_FILE_PREFIX,
                    TEMP_FILE_SUFFIX);
        }
        finally
        {
            logger.logExit();
        }

        return retVal;
    }

    public File getTemporaryDirectory() throws IOException
    {
        File file = getTemporaryFile();

        if (!file.delete())
        {
            throw new IOException("Could not create temporary directory, "
                    + "because: could not delete temporary file.");
        }
        if (!file.mkdirs())
        {
            throw new IOException("Could not create temporary directory");
        }

        return file;
    }

    /**
     * This method returns a file specific to the current user. It may not
     * exist, but it is guaranteed that you will have the sufficient rights to
     * create it.
     *
     * This file should not be considered secure because the implementor may
     * return a file accesible to everyone. Generaly it will reside in current
     * user's homedir, but it may as well reside in a shared directory.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param fileName
     *            The name of the private file you wish to access
     * @return The file
     * @throws Exception if we faile to create the file.
     */
    public File getPrivatePersistentFile(String fileName)
        throws Exception
    {

        File file = null;

        try
        {
            logger.logEntry();

            String fullPath = getFullPath(fileName);
            file = this.accessibleFile(fullPath, fileName);

            if (file == null) {
                throw new SecurityException("Insufficient rights to access "
                        + "this file in current user's home directory: "
                        + file.getAbsolutePath());
            }
        }
        finally
        {
            logger.logExit();
        }

        return file;
    }

    /**
     * This method creates a directory specific to the current user.
     *
     * This directory should not be considered secure because the implementor
     * may return a directory accesible to everyone. Generaly it will reside in
     * current user's homedir, but it may as well reside in a shared directory.
     *
     * It is guaranteed that you will be able to create files in it.
     *
     * Note: DO NOT store unencrypted sensitive information in this file
     *
     * @param dirName
     *            The name of the private directory you wish to access.
     * @return The created directory.
     * @throws Exception
     *             Thrown if there is no suitable location for the persistent
     *             directory.
     */
    public File getPrivatePersistentDirectory(String dirName)
        throws Exception
    {
        String fullPath = getFullPath(dirName);
        File dir = new File(fullPath, dirName);

        if (dir.exists())
        {
            if (!dir.isDirectory())
            {
                throw new RuntimeException("Could not create directory "
                        + "because: A file exists with this name:"
                        + dir.getAbsolutePath());
            }
        }
        else
        {
            if (!dir.mkdirs())
            {
                throw new IOException("Could not create directory");
            }
        }

        return dir;
    }

    /**
     * This method creates a directory specific to the current user.
     *
     * {@link #getPrivatePersistentDirectory(String)}
     *
     * @param dirNames
     *            The name of the private directory you wish to access.
     * @return The created directory.
     * @throws Exception
     *             Thrown if there is no suitable location for the persistent
     *             directory.
     */
    public File getPrivatePersistentDirectory(String[] dirNames)
        throws Exception
    {
        StringBuffer dirName = new StringBuffer();
        for (int i = 0; i < dirNames.length; i++)
        {
            if (i > 0)
            {
                dirName.append(File.separatorChar);
            }
            dirName.append(dirNames[i]);
        }

        return getPrivatePersistentDirectory(dirName.toString());
    }

    /**
     * Returns the full parth corresponding to a file located in the
     * sip-communicator config home and carrying the specified name.
     * @param fileName the name of the file whose location we're looking for.
     * @return the config home location of a a file withe the specified name.
     */
    private String getFullPath(String fileName)
    {

        String userhome =  this.configurationService.getScHomeDirLocation();
        String sipSubdir = this.configurationService.getScHomeDirName();

        if (!userhome.endsWith(File.separator))
        {
            userhome += File.separator;
        }
        if (!sipSubdir.endsWith(File.separator))
        {
            sipSubdir += File.separator;
        }

        return userhome + sipSubdir;
    }

    /**
     * Checks if a file exists and if it is writable or readable. If not -
     * checks if the user has a write privileges to the containing directory.
     *
     * If those conditions are met it returns a File in the directory with a
     * fileName. If not - returns null.
     *
     * @param homedir the location of the sip-communicator home directory.
     * @param fileName the name of the file to create.
     * @return Returns null if the file does not exist and cannot be created.
     *         Otherwise - an object to this file
     * @throws IOException
     *             Thrown if the home directory cannot be created
     */
    private File accessibleFile(String homedir, String fileName)
            throws IOException
    {
        File file = null;

        try
        {
            logger.logEntry();

            homedir = homedir.trim();
            if (!homedir.endsWith(File.separator)) {
                homedir += File.separator;
            }

            file = new File(homedir + fileName);
            if (file.canRead() || file.canWrite()) {
                return file;
            }

            File homedirFile = new File(homedir);

            if (!homedirFile.exists())
            {
                logger.debug("Creating home directory : "
                        + homedirFile.getAbsolutePath());
                if (!homedirFile.mkdirs()) {
                    String message = "Could not create the home directory : "
                            + homedirFile.getAbsolutePath();

                    logger.debug(message);
                    throw new IOException(message);
                }
                logger.debug("Home directory created : "
                        + homedirFile.getAbsolutePath());
            }
            else if (!homedirFile.canWrite())
            {
                file = null;
            }

        } finally
        {
            logger.logExit();
        }

        return file;
    }

}
