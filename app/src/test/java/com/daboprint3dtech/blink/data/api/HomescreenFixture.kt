package com.daboprint3dtech.blink.data.api

/** Risposta /homescreen di esempio (struttura reale, dati inventati). */
const val HOMESCREEN_JSON = """
{
  "account": {"id": 4242, "email_verified": true},
  "networks": [
    {"id": 2001, "name": "Casa", "time_zone": "Europe/Rome", "armed": true, "lv_save": false},
    {"id": 2002, "name": "Garage", "armed": false}
  ],
  "sync_modules": [
    {"id": 3001, "network_id": 2001, "status": "online", "name": "SM Casa"}
  ],
  "cameras": [
    {
      "id": 4001, "name": "Ingresso", "serial": "B001", "fw_version": "10.62", "type": "catalina",
      "enabled": true, "thumbnail": "/media/production/account/4242/network/2001/camera/4001/clip_HIwo6g_2020_10_02__02_28PM",
      "status": "done", "battery": "ok", "network_id": 2001,
      "signals": {"lfr": 5, "wifi": 4, "temp": 68, "battery": 3}
    },
    {
      "id": 4002, "name": "Giardino", "type": "sedona", "enabled": false,
      "thumbnail": "/api/v3/media/accounts/4242/networks/2001/sedona/4002/thumbnail/thumbnail.jpg?ts=1700000000&ext=",
      "status": "offline", "battery": "low", "network_id": 2001,
      "signals": {"lfr": 2, "wifi": 1, "temp": 50, "battery": 1}
    }
  ],
  "owls": [
    {"id": 5001, "name": "Salotto", "type": "owl", "enabled": true, "thumbnail": "1700000600",
     "status": "online", "network_id": 2002, "onboarded": true, "fw_version": "9.1"}
  ],
  "doorbells": [
    {"id": 6001, "name": "Campanello", "type": "lotus", "enabled": true,
     "thumbnail": "/api/v3/media/accounts/4242/networks/2002/lotus/6001/thumbnail/thumbnail.jpg?ts=1700000300&ext=",
     "status": "done", "battery": "ok", "network_id": 2002, "signals": {"wifi": 3}}
  ],
  "sirens": [], "chimes": [], "doorbell_buttons": []
}
"""
