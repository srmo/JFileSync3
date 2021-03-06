/*
 * Copyright (C) 2010-2013, Martin Goellnitz
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA, 02110-1301, USA
 */
package jfs.sync.meta;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import javax.crypto.Cipher;
import jfs.sync.encryption.FileInfo;
import jfs.sync.encryption.JFSEncryptedStream;
import jfs.sync.util.SecurityUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MetaFileStorageAccess extends AbstractMetaStorageAccess {

    private static final Log log = LogFactory.getLog(MetaFileStorageAccess.class);

    private static long outputStreams;

    private static long inputStreams;


    public MetaFileStorageAccess(String cipher, boolean sixBits) {
        super(cipher, sixBits);
    } // MetaFileStorageAccess()


    /**
     * create non existent files
     *
     * @param file
     * @param pathAndName
     * @return
     */
    private FileInfo createFileInfo(File file, String[] pathAndName) {
        FileInfo result = new FileInfo();

        result.setCanRead(true);
        result.setCanWrite(true);
        result.setDirectory(file.isDirectory());
        result.setExists(file.exists());
        result.setModificationDate(file.lastModified());
        result.setName(pathAndName[1]);
        result.setPath(pathAndName[0]);
        result.setSize(result.isDirectory() ? 0 : file.length());

        if (log.isDebugEnabled()) {
            log.debug("createFileInfo("+pathAndName[0]+"/"+pathAndName[1]+") "+result);
        } // if

        return result;
    } // createFileInfo()


    @Override
    public FileInfo getFileInfo(String rootPath, String relativePath) {
        String[] pathAndName = getPathAndName(relativePath);
        FileInfo result = getParentListing(rootPath, pathAndName).get(pathAndName[1]);
        if (result==null) {
            result = createFileInfo(getFile(rootPath, relativePath), pathAndName);
        } // if
        return result;
    } // getFileInfo()


    @Override
    public boolean createDirectory(String rootPath, String relativePath) {
        File file = getFile(rootPath, relativePath);
        boolean success = file.mkdir();
        if (success) {
            String[] pathAndName = getPathAndName(relativePath);
            Map<String, FileInfo> listing = getParentListing(rootPath, pathAndName);
            if (log.isDebugEnabled()) {
                log.debug("createDirectory() "+relativePath);
                log.debug("createDirectory() listing="+listing);
            } // if
            FileInfo info = createFileInfo(file, pathAndName);
            listing.put(pathAndName[1], info);
            if (log.isDebugEnabled()) {
                log.debug("createDirectory() listing="+listing);
            } // if
            if (log.isInfoEnabled()) {
                log.info("createDirectory() flushing "+pathAndName[0]+"/"+pathAndName[1]);
            } // if
            flushMetaData(rootPath, pathAndName, listing);
        } // if
        if (log.isDebugEnabled()) {
            log.debug("createDirectory() "+success);
        } // if
        return success;
    } // createDirectory()


    @Override
    public boolean setLastModified(String rootPath, String relativePath, long modificationDate) {
        boolean success = getFile(rootPath, relativePath).setLastModified(modificationDate);
        if (success) {
            String[] pathAndName = getPathAndName(relativePath);
            Map<String, FileInfo> listing = getParentListing(rootPath, pathAndName);
            FileInfo info = listing.get(pathAndName[1]);
            if (log.isInfoEnabled()) {
                log.info("setLastModified() flushing "+pathAndName[0]+"/"+pathAndName[1]);
            } // if
            info.setModificationDate(modificationDate);
            flushMetaData(rootPath, pathAndName, listing);
        } // if
        return success;
    } // setLastModified()


    @Override
    public boolean setReadOnly(String rootPath, String relativePath) {
        if (log.isInfoEnabled()) {
            log.info("setReadOnly() not flushing "+relativePath);
        } // if
        return getFile(rootPath, relativePath).setReadOnly();
    } // setReadOnly()


    @Override
    public boolean delete(String rootPath, String relativePath) {
        String[] pathAndName = getPathAndName(relativePath);
        Map<String, FileInfo> listing = getParentListing(rootPath, pathAndName);
        if (log.isDebugEnabled()) {
            log.debug("delete() "+relativePath);
            log.debug("delete() listing="+listing);
        } // if
          // remove named item
        File file = getFile(rootPath, relativePath);
        if (listing.containsKey(pathAndName[1])) {
            listing.remove(pathAndName[1]);
            if (log.isInfoEnabled()) {
                log.info("delete() flushing "+pathAndName[0]+"/"+pathAndName[1]);
            } // if
            flushMetaData(rootPath, pathAndName, listing);
            if (log.isInfoEnabled()) {
                log.info("delete() listing="+listing);
            } // if
            if (file.isDirectory()) {
                String metaDataPath = getMetaDataPath(relativePath);
                File metaDataFile = getFile(rootPath, metaDataPath);
                if (metaDataFile.exists()) {
                    metaDataFile.delete();
                } // if
            } // if
            file.delete();
        } // if
        return !file.exists();
    } // delete()


    @Override
    public InputStream getInputStream(String rootPath, String relativePath) throws IOException {
        File file = getFile(rootPath, relativePath);
        if (log.isDebugEnabled()) {
            log.debug("getInputStream() getting input stream for "+file.getPath());
        } // if
        inputStreams++ ;
        if (log.isDebugEnabled()) {
            log.debug("getInputStream("+relativePath+") inputStreams="+inputStreams);
        } // if
        return new FileInputStream(file);
    } // getInputStream()


    @Override
    protected OutputStream getOutputStream(String rootPath, String relativePath, boolean forPayload) throws IOException {
        File file = getFile(rootPath, relativePath);
        String[] pathAndName = getPathAndName(relativePath);
        if (forPayload&&( !file.exists())) {
            FileInfo info = createFileInfo(file, pathAndName);
            Map<String, FileInfo> listing = getParentListing(rootPath, pathAndName);
            listing.put(info.getName(), info);
            if (log.isInfoEnabled()) {
                log.info("getOutputStream() flushing "+pathAndName[0]+"/"+pathAndName[1]);
            } // if
            flushMetaData(rootPath, pathAndName, listing);
            if (log.isDebugEnabled()) {
                log.debug("getOutputStream() getting output stream for "+file.getPath()+" "+info);
            } // if
        } // if
        if (log.isDebugEnabled()) {
            log.debug("getOutputStream() getting output stream for "+file.getPath());
        } // if
        outputStreams++ ;
        if (log.isInfoEnabled()) {
            log.info("getOutputStream("+relativePath+") outputStreams="+outputStreams);
        } // if
        OutputStream result = new FileOutputStream(file);
        FileInfo info = createFileInfo(file, pathAndName);
        if (log.isDebugEnabled()) {
            log.debug("getOutputStream() have output stream for "+file.getPath()+" "+info+" "+result);
        } // if
        return result;
    } // getOutputStream()


    /**
     *
     * Extract one file from encrypted repository.
     *
     * TODO: list directories
     *
     */
    public static void main(String[] args) throws Exception {
        final String password = args[0];
        MetaFileStorageAccess storage = new MetaFileStorageAccess("Twofish", false) {

            protected String getPassword(String relativePath) {
                String result = "";

                String pwd = password;

                int i = 0;
                int j = relativePath.length()-1;
                while ((i<pwd.length())||(j>=0)) {
                    if (i<pwd.length()) {
                        result += pwd.charAt(i++ );
                    } // if
                    if (j>=0) {
                        result += relativePath.charAt(j-- );
                    } // if
                } // while

                return result;
            } // getPassword()
        };
        String encryptedPath = args[2];
        String[] elements = encryptedPath.split("\\\\");
        String path = "";
        for (int i = 0; i<elements.length; i++ ) {
            String encryptedName = elements[i];
            String plain = storage.getDecryptedFileName(path, encryptedName);
            path += storage.getSeparator();
            path += plain;
            System.out.println(path);
        } // for

        String cipherSpec = storage.getCipherSpec();
        byte[] credentials = storage.getFileCredentials(path);
        Cipher cipher = SecurityUtils.getCipher(cipherSpec, Cipher.DECRYPT_MODE, credentials);

        InputStream is = storage.getInputStream(args[1], path);
        is = JFSEncryptedStream.createInputStream(is, JFSEncryptedStream.DONT_CHECK_LENGTH, cipher);
        int b = 0;
        while (b>=0) {
            b = is.read();
            System.out.print((char)b);
        } // while
        is.close();
    } // main()

} // MetaFileStorageAccess
