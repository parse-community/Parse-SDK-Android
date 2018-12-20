package com.parse;

import android.os.Parcel;

import java.util.HashMap;
import java.util.Map;

/**
 * This is a stateful implementation of {@link ParseParcelDecoder} that remembers which
 * {@code ParseObject}s have been decoded. When a pointer is found and we have already decoded
 * an instance for the same object id, we use the decoded instance.
 * <p>
 * This is very similar to what {@link KnownParseObjectDecoder} does for JSON.
 */
/* package */ class ParseObjectParcelDecoder extends ParseParcelDecoder {

    private Map<String, ParseObject> objects = new HashMap<>();

    public ParseObjectParcelDecoder() {
    }

    public void addKnownObject(ParseObject object) {
        objects.put(getObjectOrLocalId(object), object);
    }

    @Override
    protected ParseObject decodePointer(Parcel source) {
        String className = source.readString();
        String objectId = source.readString();
        if (objects.containsKey(objectId)) {
            return objects.get(objectId);
        }
        // Should not happen if encoding was done through ParseObjectParcelEncoder.
        ParseObject object = ParseObject.createWithoutData(className, objectId);
        objects.put(objectId, object);
        return object;
    }

    private String getObjectOrLocalId(ParseObject object) {
        return object.getObjectId() != null ? object.getObjectId() : object.getOrCreateLocalId();
    }
}
