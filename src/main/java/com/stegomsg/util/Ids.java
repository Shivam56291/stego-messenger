package com.stegomsg.util;

import java.util.UUID;

/** Central place for generating opaque identifiers — never expose raw DB row numbers. */
public final class Ids {
    private Ids() {}

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
