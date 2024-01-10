package org.chernovia.lib.zugserv;

public interface ZugFields {
    enum AuthSource { none, local, lichess, twitch, google }
    enum ServMsgType { none, obs, unObs, reqLogin, logOK, noLog, errMsg, servMsg, servUserMsg, areaUserMsg, areaMsg, roomMsg, privMsg,
        updateUsers, updateAreas, updateArea, updateRoom, updateServ, updateUser, updateOccupant, updateOptions }
    enum ClientMsgType { none, obs, unObs, login, loginGuest, loginLichess, getOptions, setOptions, newRoom, joinRoom, newArea, joinArea, partArea, areaMsg, roomMsg, servMsg, privMsg,
        updateArea, updateRoom, updateServ, updateUser, updateOccupant, setMute
    }

    String UNKNOWN_STRING = "STRING_UNKNOWN"; //TODO: get rid of this in favor of either Optional or @Nullable
    String TYPE = "type",
            DATA = "data",
            MSG = "msg",
            NAME = "name",
            SOURCE = "source",
            USER = "user",
            USERS = "users",
            JSON = "json",
            TITLE = "title",
            ROOM = "room",
            AREA = "area",
            AREAS = "areas",
            CREATOR = "creator",
            TOKEN = "token",
            LOGGED_IN = "logged_in",
            OCCUPANTS = "occupants",
            OBSERVERS = "observers",
            MUTED = "muted",
            VAL = "val",
            MIN = "min",
            MAX = "max",
            INT = "int",
            OPTIONS = "options";
}
