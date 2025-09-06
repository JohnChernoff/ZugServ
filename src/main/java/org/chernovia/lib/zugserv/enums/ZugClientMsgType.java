package org.chernovia.lib.zugserv.enums;

public enum ZugClientMsgType {
    none, ip, pong, obs, unObs, login, loginGuest, loginLichess, getOptions, setOptions, listAreas, getMessages,
    newRoom, joinRoom, newArea, joinArea, startArea, partArea, areaMsg, roomMsg, servMsg, privMsg, updateArea, updateRoom,
    updateServ, updateUser, updateOccupant, setDeaf, ban, kick, response, nudge, clockRequest
}
