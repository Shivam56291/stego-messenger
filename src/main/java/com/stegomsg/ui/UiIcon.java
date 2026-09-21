package com.stegomsg.ui;

import javafx.scene.Node;
import javafx.scene.shape.SVGPath;

/**
 * Small built-in vector icon set so the UI does not depend on an external icon/font package.
 * Paths are kept monochrome and are recolored by the application's CSS.
 */
public final class UiIcon {

    private UiIcon() {}

    public enum Name {
        SHIELD, MAIL, LOCK, USER, EYE, EYE_OFF, CHAT, IMAGE, HISTORY,
        SETTINGS, LOGOUT, PLUS, SEND, SEARCH, CHECK, WARNING, INFO, ARROW_RIGHT,
        REFRESH, KEY
    }

    public static Node icon(Name name, double size) {
        SVGPath path = new SVGPath();
        path.setContent(pathData(name));
        path.setScaleX(size / 24.0);
        path.setScaleY(size / 24.0);
        path.getStyleClass().add("ui-icon");
        path.setMouseTransparent(true);
        return path;
    }

    private static String pathData(Name name) {
        return switch (name) {
            case SHIELD -> "M12 2 20 5v6c0 5.2-3.4 9.9-8 11-4.6-1.1-8-5.8-8-11V5l8-3Zm0 3.1L7 7v4c0 3.7 2.2 7.1 5 8.1 2.8-1 5-4.4 5-8.1V7l-5-1.9Z";
            case MAIL -> "M3 5.5C3 4.7 3.7 4 4.5 4h15c.8 0 1.5.7 1.5 1.5v13c0 .8-.7 1.5-1.5 1.5h-15C3.7 20 3 19.3 3 18.5v-13ZM5 6v.4l7 5.2 7-5.2V6H5Zm14 2.9-6.4 4.8a1 1 0 0 1-1.2 0L5 8.9v9.1h14V8.9Z";
            case LOCK -> "M7 10V7a5 5 0 0 1 10 0v3h1a2 2 0 0 1 2 2v8H4v-8a2 2 0 0 1 2-2h1Zm2 0h6V7a3 3 0 0 0-6 0v3Zm3 3a1.8 1.8 0 0 0-1 3.3V18h2v-1.7A1.8 1.8 0 0 0 12 13Z";
            case USER -> "M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9Zm0 2c-4.4 0-8 2.2-8 5v1h16v-1c0-2.8-3.6-5-8-5Z";
            case EYE -> "M2.3 12s3.2-6 9.7-6 9.7 6 9.7 6-3.2 6-9.7 6-9.7-6-9.7-6Zm9.7 3.5A3.5 3.5 0 1 0 12 8a3.5 3.5 0 0 0 0 7.5Z";
            case EYE_OFF -> "m4 4 16 16-1.4 1.4-3-3C14.4 19.5 13.2 20 12 20c-6.5 0-9.7-6-9.7-6 .9-1.7 2-3.1 3.4-4.1L2.6 5.4 4 4Zm5.6 5.6 5 5A3.5 3.5 0 0 0 9.6 9.6ZM12 6c6.5 0 9.7 6 9.7 6-.8 1.5-1.8 2.8-3 3.8l-1.4-1.4c.8-.7 1.5-1.5 2.1-2.4-1-1.3-3.5-4-7.4-4-1 0-2 .2-2.8.5L8 7.1A11 11 0 0 1 12 6Z";
            case CHAT -> "M4 4h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9l-5 4v-4H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm0 2v9.6L8.3 16H20V6H4Zm2 3h12v2H6V9Zm0 4h8v2H6v-2Z";
            case IMAGE -> "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm0 2v10.9l4.2-4.2 3.2 3.2 2.7-3.4L20 18.5V6H4Zm2.5 3A1.5 1.5 0 1 0 6.5 6a1.5 1.5 0 0 0 0 3Z";
            case HISTORY -> "M12 4a8 8 0 1 1-7.7 10H2l3.5-3.5L9 14H6.4A6 6 0 1 0 12 6a5.9 5.9 0 0 0-4.2 1.8L6.4 6.4A8 8 0 0 1 12 4Zm-1 3h2v4l3 1.8-1 1.7-4-2.4V7Z";
            case SETTINGS -> "M9.7 2h4.6l.6 2.2c.6.2 1.2.5 1.7 1l2.2-.7 3.3 3.3-1 2.1c.4.5.7 1.1.9 1.7L24 12v4l-2.1.5c-.2.6-.5 1.2-.9 1.7l1 2.1-3.3 3.3-2.2-.7c-.5.4-1.1.7-1.7.9L14.3 26H9.7l-.6-2.2c-.6-.2-1.2-.5-1.7-.9l-2.2.7L1.9 20.3l1-2.1c-.4-.5-.7-1.1-.9-1.7L0 16v-4l2.1-.5c.2-.6.5-1.2.9-1.7l-1-2.1L5.2 4.4l2.2.7c.5-.4 1.1-.7 1.7-.9L9.7 2Zm2.3 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10Zm0 2a3 3 0 1 1 0 6 3 3 0 0 1 0-6Z";
            case LOGOUT -> "M10 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h5v-2H5V6h5V4Zm5 3 6 5-6 5v-3h-6v-4h6V7Z";
            case PLUS -> "M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6V5Z";
            case SEND -> "m3 4 18 8-18 8 2.6-6.4L14 12l-8.4-1.6L3 4Zm3.5 5.6L5.2 13h8.5v-2H5.2l1.3-1.4Z";
            case SEARCH -> "M10.5 3a7.5 7.5 0 1 0 4.7 13.3L20 21.1 21.4 19.7l-4.8-4.8A7.5 7.5 0 0 0 10.5 3Zm0 2a5.5 5.5 0 1 1 0 11 5.5 5.5 0 0 1 0-11Z";
            case CHECK -> "m9.2 16.6-4-4L3.8 14l5.4 5.4L20.2 8.4 18.8 7 9.2 16.6Z";
            case WARNING -> "M12 3 22 21H2L12 3Zm0 5.2L5.4 19h13.2L12 8.2ZM11 11h2v4h-2v-4Zm0 5h2v2h-2v-2Z";
            case INFO -> "M11 10h2v7h-2v-7Zm0-4h2v2h-2V6Zm1-4a10 10 0 1 0 0 20 10 10 0 0 0 0-20Z";
            case ARROW_RIGHT -> "m13 5 7 7-7 7-1.4-1.4 4.6-4.6H4v-2h15.2l-4.6-4.6L13 5Z";
            case REFRESH -> "M12 5a7 7 0 0 0-6.7 5H3l3.5 3.5L10 10H7.4A5 5 0 1 1 12 17c-1.8 0-3.4-1-4.3-2.4l-1.7 1A7 7 0 1 0 12 5Z";
            case KEY -> "M15 4a5 5 0 0 0-4.9 6H3v4h3v3h3v-3h1.1A5 5 0 1 0 15 4Zm0 2a3 3 0 1 1 0 6 3 3 0 0 1 0-6Z";
        };
    }
}
