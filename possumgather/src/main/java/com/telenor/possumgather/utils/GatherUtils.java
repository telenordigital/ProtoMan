package com.telenor.possumgather.utils;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GatherUtils {
    private static final String tag = GatherUtils.class.getName();

    /**
     * Creates a zipStream for storing data
     * @param innerStream the stream it is supposed to wrap in
     * @param name the entryName it should use
     * @return a zipStream with given entry
     * @throws IOException should it fail to create the stream
     */
    public static ZipOutputStream createZipStream(@NonNull OutputStream innerStream, @NonNull String name) throws IOException {
        ZipOutputStream zipStream = new ZipOutputStream(innerStream);
        ZipEntry entry = new ZipEntry(name);
        zipStream.putNextEntry(entry);
        return zipStream;
    }

    /**
     * Retrieves the storage catalogue for upload files. Will create if not already there.
     * @param context a valid android context
     * @return the File representing the directory where files can be saved or null if it is unable
     * to get it due to permissions or already being a file there with that directory's name
     */
    public static File storageCatalogue(@NonNull Context context) {
        File storageCatalogue = new File(context.getFilesDir().getAbsolutePath() + "/APData");
        if (storageCatalogue.exists() && storageCatalogue.isDirectory()) return storageCatalogue;
        else if (!storageCatalogue.exists()) {
            if (storageCatalogue.mkdir()) return storageCatalogue;
            else return null;
        } else {
            Log.e(tag, "AP: Storage catalogue is unable to get it because there already exists a file with its name");
            return null;
        }
    }

    /**
     * Retrieves all files stored in the designated storage catalogue
     * @param context a valid android context
     * @return an array with all the files stored in the designated space. If no files are present,
     * an empty array is returned
     */
    public static List<File> getFiles(@NonNull Context context) {
        List<File> filesFound = new ArrayList<>();
        File catalogue = storageCatalogue(context);
        if (catalogue == null) return filesFound;
        Collections.addAll(filesFound, catalogue.listFiles());
        return filesFound;
    }
}