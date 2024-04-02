package org.chernovia.lib.zugserv;

public interface ZugFields {
    enum AuthSource { none, local, lichess, twitch, google, bot }
    enum ServMsgType { none, ip, ipReq, ping, obs, unObs, reqLogin, logOK, noLog, errMsg, alertMsg, servMsg, servUserMsg, areaUserMsg, areaMsg, roomMsg, privMsg,
        joinArea, partArea, createArea, startArea, updateUsers, updateAreas, updateArea, updateRoom, updateServ, updateUser, updateOccupant, updateOccupants, updateOptions
    }
    enum ClientMsgType { none, ip, pong, obs, unObs, login, loginGuest, loginLichess, getOptions, setOptions,
        newRoom, joinRoom, newArea, joinArea, startArea, partArea, areaMsg, roomMsg, servMsg, privMsg, updateArea, updateRoom, updateServ, updateUser, updateOccupant, setMute
    }

    String UNKNOWN_STRING = "STRING_UNKNOWN"; //TODO: get rid of this in favor of either Optional or @Nullable
    String TYPE = "type",
            ADDRESS = "address",
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
            OCCUPANT = "occupant",
            OCCUPANTS = "occupants",
            OBSERVERS = "observers",
            MUTED = "muted",
            VAL = "val",
            MIN = "min",
            MAX = "max",
            INC = "inc",
            CHAT_COLOR = "chat_color",
            OPTIONS = "options";
}
