package com.parse;

import android.os.Parcel;

import java.util.HashSet;
import java.util.Set;

/**
 * This is a stateful implementation of {@link ParseParcelEncoder} that remembers which
 * {@code ParseObject}s have been encoded. If an object is found again in the object tree,
 * it is encoded as a pointer rather than a full object, to avoid {@code StackOverflowError}s
 * due to circular references.
 */
/* package */ class ParseObjectParcelEncoder extends ParseParcelEncoder {

  private Set<String> ids = new HashSet<>();

  public ParseObjectParcelEncoder() {}

  public ParseObjectParcelEncoder(ParseObject root) {
    ids.add(getObjectOrLocalId(root));
  }

  @Override
  protected void encodeParseObject(ParseObject object, Parcel dest) {
    String id = getObjectOrLocalId(object);
    if (ids.contains(id)) {
      encodePointer(object.getClassName(), id, dest);
    } else {
      ids.add(id);
      super.encodeParseObject(object, dest);
    }
  }

  private String getObjectOrLocalId(ParseObject object) {
    return object.getObjectId() != null ? object.getObjectId() : object.getOrCreateLocalId();
  }
}
