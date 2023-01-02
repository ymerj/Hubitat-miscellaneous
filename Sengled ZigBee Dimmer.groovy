/**
 *	Copyright 2019 SmartThings
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 */

//   Original source: https://github.com/SmartThingsCommunity/SmartThingsPublic/blob/master/devicetypes/smartthings/zigbee-battery-accessory-dimmer.src/zigbee-battery-accessory-dimmer.groovy
//   modified by Yves Mercier for discrete level step

import hubitat.zigbee.zcl.DataType

metadata
    {
    definition (name: "Sengled ZigBee Dimmer", namespace: "ymerj", author: "SmartThings")
        {
        capability "Actuator"
        capability "Configuration"
        capability "Switch"
        capability "Switch Level"

        command "levelChange", [[ name: "Direction", type: "STRING", description: "Level direction" ]]

        fingerprint profileId: "0104", inClusters: "0000,0001,0003,0020,FC11", outClusters: "0003,0004,0006,0008,FC10", manufacturer: "sengled", model: "E1E-G7F", deviceJoinName: "Sengled Smart Switch"
        }
    preferences
        {        
        input name: "step1", type: "number", title: "Bas %", defaultValue: "10", required: true 
        input name: "step2", type: "number", title: "Medium %", defaultValue: "26", required: true
        input name: "step3", type: "number", title: "Haut %", defaultValue: "50", required: true
        input name: "onValue", type: "enum", title: "Initial level when turned On", options: ["Bright", "Dim", "Level"], defaultValue: "Level"
        input name: "debuglog", type: "bool", title: "Enable debug logging", defaultValue: false
        }
    }

def getONOFF_ON_COMMAND() { 0x0001 }
def getONOFF_OFF_COMMAND() { 0x0000 }
def getLEVEL_MOVE_LEVEL_COMMAND() { 0x0000 }
def getLEVEL_MOVE_COMMAND() { 0x0001 }
def getLEVEL_STEP_COMMAND() { 0x0002 }
def getLEVEL_STOP_COMMAND() { 0x0003 }
def getLEVEL_MOVE_LEVEL_ONOFF_COMMAND() { 0x0004 }
def getLEVEL_MOVE_ONOFF_COMMAND() { 0x0005 }
def getLEVEL_STEP_ONOFF_COMMAND() { 0x0006 }
def getLEVEL_STOP_ONOFF_COMMAND() { 0x0007 }
def getLEVEL_DIRECTION_UP() { "00" }
def getLEVEL_DIRECTION_DOWN() { "01" }
def getMFR_SPECIFIC_CLUSTER() { 0xFC10 }

def parse(String description)
    {
    def descMap = zigbee.parseDescriptionAsMap(description)    
    def bouton = descMap?.data[0]
    exec(bouton)
    } 

def exec(bouton)
    {
    def currentLevel = device.currentValue("level")
    def newLevel = currentLevel
    switch (bouton)
        {
        case '01':
            if (onValue == "Bright") sendEvent(name: "level", value: 99)
            else if ((currentLevel < step1) || (onValue == "Dim")) sendEvent(name: "level", value: step1)
            sendEvent(name: "switch", value: "on")
        break
        case '02':
            newLevel = nextStepUp(currentLevel)                
            sendEvent(name: "level", value: newLevel)
        break
        case '03':
            newLevel = nextStepDown(currentLevel)
            sendEvent(name: "level", value: newLevel)
        break
        case '04':
            sendEvent(name: "switch", value: "off")
        break
        }
    }

def nextStepUp(currentLevel)
    {
    def newLevel = 0
    switch (currentLevel)
        {
        case step1:
            newLevel = step2
        break
        case step2:
            newLevel = step3
        break
        default:
            newLevel = 99
        break
        }
    return newLevel
    }
                
def nextStepDown(currentLevel)
    {
    def newLevel = 0
    switch (currentLevel)
        {
        case 99:
            newLevel = step3
        break
        case step3:
            newLevel = step2
        break
        default:
            newLevel = step1
        break
        }
    return newLevel
    }

def off() 
    {
    exec("04")
    }

def on() 
    {
    exec("01")
    }

def setLevel(value, rate = null)
    {
    def newLevel = 99
    newLevel = [step1, step2, step3, 99].find {it >= value}
    sendEvent(name: "level", value: newLevel)
    sendEvent(name: "switch", value: "on")
    }

def levelChange(direction)
    {
    def bouton = "01"
    if (direction == "up") bouton = "02"
    if (direction == "down") bouton = "03"
    exec(bouton)
    }

def installed()
    {
    sendEvent(name: "level", value: 99)
    sendEvent(name: "switch", value: "on")
    }

def configure() 
    {
    installed()
    }
