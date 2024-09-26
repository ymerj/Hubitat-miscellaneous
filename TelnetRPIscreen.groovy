/*
* Telnet driver for RPI screen shutoff
*
* Description:
* Allow control of RPI screen.
*
* Requirement:
* Telnet server installed on RPI
*
* Features List:
*
* Licensing:
* Copyright 2024 Yves Mercier.
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
* in compliance with the License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
* for the specific language governing permissions and limitations under the License.
*
* Version Control:
* 1.0  2024-02-05 Yves Mercier    Original version
* 1.1  2024-09-25 Yves Mercier    Connect and disconnect for each command
* 1.2  2024-09-26 Yves Mercier    Add connection status check
*/

metadata
    {
    definition(name: "Telnet RPI screen", namespace: "ymerj", author: "Yves Mercier")
        {
        capability "Actuator"
        capability "Switch"
        capability "Refresh"
        capability "Telnet"
        capability "Initialize"

        command "closeConnection"    
        }

    preferences
        {
        input name: "deviceIp", type: "text", title: "Device IP", defaultValue:"192.168.0.100"
        input name: "port", type: "integer", title: "Device Port", defaultValue:"23"
        input name: "user", type: "text", title: "User name"
        input name: "pass", type: "text", title: "Password"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        }
    }

def installed()
    {
    log.warn "installed"
    state.lastEtat = "unknown"
    updated()
    }

def updated()
    {
    log.warn "updated"
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    initialize()
    }

def initialize()
    {
    try
        {
        telnetConnect([terminalType: "VT100", termChars:[10]], deviceIp, port.toInteger(), user, pass)
        } 
    catch(e)
        {
        log.error("initialize error: ${e.message}")
        }
    }

def parse(String msg)
    {
    if (logEnable) log.info msg
    if (msg.contains("display_power"))
        {
        value = msg.contains("display_power=1")? "on":"off"
        sendEvent(name: "switch", value: value, descriptionText: "${device.label} was turn ${value}")
        }
    if (msg.contains("Last login")) telnetStatus("connected")
    }

def on()
    {
    sendMsg("vcgencmd display_power 1")
    }
    
def off()
    {
    sendMsg("vcgencmd display_power 0")
    }

def refresh()
    {
    sendMsg("vcgencmd display_power")
    }

def sendMsg(message)
    {
    if (state.lastEtat != "connected") initialize()
    pauseExecution(1000)
    sendHubCommand(new hubitat.device.HubAction(message, hubitat.device.Protocol.TELNET))
    }

def telnetStatus(etat)
    {
    state.lastEtat = etat
    sendEvent(name: "networkStatus", value: etat)
    }

def closeConnection()
    {
    try
        {
        telnetClose()
        }
    catch(e)
        {
        log.error("close error: ${e.message}")       
        }
    }

def logsOff()
    {
    log.warn "debug logging disabled"
    device.updateSetting("logEnable",[value:"false",type:"bool"])
    }
