package com.parse;

import android.content.Context;
import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKey;
import com.parse.boltsinternal.Task;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Callable;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * a file based {@link ParseObjectStore} using Jetpack's {@link EncryptedFile} class to protect
 * files from a malicious copy.
 */
class EncryptedFileObjectStore<T extends ParseObject> implements ParseObjectStore<T> {

    private final String className;
    private final File file;
    private final EncryptedFile encryptedFile;
    private final ParseObjectCurrentCoder coder;

    public EncryptedFileObjectStore(Class<T> clazz, File file, ParseObjectCurrentCoder coder) {
        this(getSubclassingController().getClassName(clazz), file, coder);
    }

    public EncryptedFileObjectStore(String className, File file, ParseObjectCurrentCoder coder) {
        this.className = className;
        this.file = file;
        this.coder = coder;
        Context context = ParsePlugins.get().applicationContext();
        try {
            encryptedFile =
                    new EncryptedFile.Builder(
                                    context,
                                    file,
                                    new MasterKey.Builder(context)
                                            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                                            .build(),
                                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB)
                            .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private static ParseObjectSubclassingController getSubclassingController() {
        return ParseCorePlugins.getInstance().getSubclassingController();
    }

    /**
     * Saves the {@code ParseObject} to the a file on disk as JSON in /2/ format.
     *
     * @param current ParseObject which needs to be saved to disk.
     * @throws IOException thrown if an error occurred during writing of the file
     * @throws GeneralSecurityException thrown if there is an error with encryption keys or during
     *     the encryption of the file
     */
    private void saveToDisk(ParseObject current) throws IOException, GeneralSecurityException {
        JSONObject json = coder.encode(current.getState(), null, PointerEncoder.get());
        ParseFileUtils.writeJSONObjectToFile(encryptedFile, json);
    }

    /**
     * Retrieves a {@code ParseObject} from a file on disk in /2/ format.
     *
     * @return The {@code ParseObject} that was retrieved. If the file wasn't found, or the contents
     *     of the file is an invalid {@code ParseObject}, returns {@code null}.
     * @throws GeneralSecurityException thrown if there is an error with encryption keys or during
     *     the encryption of the file
     * @throws JSONException thrown if an error occurred during the decoding process of the
     *     ParseObject to a JSONObject
     * @throws IOException thrown if an error occurred during writing of the file
     */
    private T getFromDisk() throws GeneralSecurityException, JSONException, IOException {
        return ParseObject.from(
                coder.decode(
                                ParseObject.State.newBuilder(className),
                                ParseFileUtils.readFileToJSONObject(encryptedFile),
                                ParseDecoder.get())
                        .isComplete(true)
                        .build());
    }

    @Override
    public Task<T> getAsync() {
        return Task.call(
                new Callable<T>() {
                    @Override
                    public T call() throws Exception {
                        if (!file.exists()) return null;
                        try {
                            return getFromDisk();
                        } catch (GeneralSecurityException e) {
                            throw new RuntimeException(e.getMessage());
                        }
                    }
                },
                ParseExecutors.io());
    }

    @Override
    public Task<Void> setAsync(T object) {
        return Task.call(
                () -> {
                    if (file.exists() && !ParseFileUtils.deleteQuietly(file))
                        throw new RuntimeException("Unable to delete");
                    try {
                        saveToDisk(object);
                    } catch (GeneralSecurityException e) {
                        throw new RuntimeException(e.getMessage());
                    }
                    return null;
                },
                ParseExecutors.io());
    }

    @Override
    public Task<Boolean> existsAsync() {
        return Task.call(file::exists, ParseExecutors.io());
    }

    @Override
    public Task<Void> deleteAsync() {
        return Task.call(
                () -> {
                    if (file.exists() && !ParseFileUtils.deleteQuietly(file))
                        throw new RuntimeException("Unable to delete");
                    return null;
                },
                ParseExecutors.io());
    }
}
