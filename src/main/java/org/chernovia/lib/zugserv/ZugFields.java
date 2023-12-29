package org.chernovia.lib.zugserv;

public interface ZugFields {
    enum ServTypes { none, reqLogin, logOK, noLog, errMsg, servMsg }
    enum ClientTypes { none, login, getOptions, setOptions }

    String UNKNOWN_STRING = "STRING_UNKNOWN"; //TODO: get rid of this in favor of either Optional or @Nullable
    String TYPE = "type",
            DATA = "data",
            MSG = "msg",
            JSON = "json",
            TITLE = "title",
            ROOM = "room",
            SOURCE = "source",
            TOKEN = "token"; //TODO: title - area?
}
