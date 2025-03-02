package org.chernovia.lib.zugserv.enums;

public enum ZugServMsgType {
    none, version, ip, ipReq, ping, obs, unObs, reqLogin, logOK, noLog, errMsg, alertMsg, servMsg, servUserMsg,
    areaUserMsg, areaMsg, roomUserMsg, roomMsg, privMsg, phase,
    joinRoom, joinArea, partArea, createArea, startArea, userList, areaList, updateAreaList, updateArea, updateRoom, updateServ,
    updateUser, updateOccupant, updateOccupants, updateOptions
}
