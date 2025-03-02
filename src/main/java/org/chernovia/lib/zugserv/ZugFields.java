package org.chernovia.lib.zugserv;

enum ZugAreaPhase {initializing,querying,finalizing}

public interface ZugFields {
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
            AREA_ID = "area_id",
            PHASE = "phase",
            ROOM = "room",
            AREA = "area",
            AREAS = "areas",
            CREATOR = "creator",
            TOKEN = "token",
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
            AUTO_JOIN = "auto_join",
            ZUG_MSG = "zug_msg",
            MSG_DATE = "zug_msg_date",
            MSG_USER = "zug_msg_user",
            ZUG_TEXT = "zug_text",
            TXT_EMOJI = "txt_emoji",
            TXT_ASCII = "txt_ascii",
            MSG_HISTORY = "msg_history",
            UPDATE_SCOPE = "up_scope";
}
