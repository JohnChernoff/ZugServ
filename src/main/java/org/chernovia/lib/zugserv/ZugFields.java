package org.chernovia.lib.zugserv;

public interface ZugFields {
    enum AuthSource { none, local, lichess, twitch, google, bot }
    enum ServMsgType { none, version, ip, ipReq, ping, obs, unObs, reqLogin, logOK, noLog, errMsg, alertMsg, servMsg, servUserMsg, areaUserMsg, areaMsg, roomMsg, privMsg, phase,
        joinRoom, joinArea, partArea, createArea, startArea, userList, areaList, updateAreaList, updateArea, updateRoom, updateServ, updateUser, updateOccupant, updateOccupants, updateOptions
    }
    enum ClientMsgType { none, ip, pong, obs, unObs, login, loginGuest, loginLichess, getOptions, setOptions, listAreas,
        newRoom, joinRoom, newArea, joinArea, startArea, partArea, areaMsg, roomMsg, servMsg, privMsg, updateArea, updateRoom, updateServ, updateUser, updateOccupant, setDeaf, ban
    }

    enum AreaChange {created,updated,deleted}

    enum AreaPhase {initializing,querying,finalizing}

    String UNKNOWN_STRING = "STRING_UNKNOWN"; //TODO: get rid of this in favor of either Optional or @Nullable
    String TYPE = "type",
            ADDRESS = "address",
            DATA = "data",
            MSG = "msg",
            UNAME = "uname", //unique name
            NAME = "name",
            SOURCE = "source",
            USER = "user",
            USERS = "users",
            JSON = "json",
            TITLE = "title",
            PHASE = "phase",
            ROOM = "room",
            AREA = "area",
            AREAS = "areas",
            CREATOR = "creator",
            TOKEN = "token",
            ID = "id",
            LOGGED_IN = "logged_in",
            OCCUPANT = "occupant",
            OCCUPANTS = "occupants",
            OBSERVERS = "observers",
            DEAFENED = "deafened",
            CHAT_COLOR = "chat_color",
            OPTIONS = "options",
            OPT_NAME = "opt_name",
            OPT_DESC = "opt_desc",
            OPT_LABEL = "opt_label",
            OPT_ENUM = "opt_enum",
            OPT_VAL = "opt_val",
            OPT_MIN = "opt_min",
            OPT_MAX = "opt_max",
            OPT_INC = "opt_inc",
            LOGIN_TYPE = "login_type",
            AREA_CHANGE = "area_change",
            PHASE_STAMP = "phase_stamp",
            PHASE_TIME_REMAINING = "phase_time_remaining",
            EXISTS = "exists",
            RUNNING = "running",
            GUEST = "guest",
            GUESTS = "guests",
            ALLOW_GUESTS = "allow_guests",
            CROWDED = "crowded",
            ACTIVE = "active",
            DAILY_USERS = "daily_users",
            UNIQUE_USERS = "unique_users",
            AUTO_JOIN = "auto_join";
}
