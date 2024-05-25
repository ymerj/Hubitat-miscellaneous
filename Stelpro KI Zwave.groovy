/**
 *  Modified version of the SmartThings Z-Wave Thermostat device handler for Stelpro STZW402+
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * 1.1 initial modifications by jrfarrar
 * 1.2 further modifications by ymerj
 * 1.3 tentative modification for homekit and hubitat new take on thermostats
 * 1.4 add poll capability
 * 1.5 refactored for change in Google Home requirements
 * 1.6 fix logging, reintroduce switch capability
 * 1.7 remove switch capability, add fake fan mode and reintroduce eco mode to comply with hubitat new dashboard app
 */
 
import groovy.json.JsonOutput

metadata
    {
    definition (name: "Stelpro Ki Thermostat", namespace: "stelpro", author: "Stelpro")
        {
        capability "Thermostat"
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability 'TemperatureMeasurement'

        attribute "supportedThermostatFanModes", "JSON_OBJECT"
        attribute "supportedThermostatModes", "JSON_OBJECT"
            
        command ("setThermostatMode", [["name":"Confirmation*", "type":"ENUM", "constraints":["heat","eco"]]])
        command ("setThermostatFanMode", [["name":"Confirmation*", "type":"ENUM", "constraints":["on"]]])

        fingerprint deviceId: "0x0806", inClusters: "0x5E,0x86,0x72,0x40,0x43,0x31,0x85,0x59,0x5A,0x73,0x20,0x42"
        }      
    preferences
        {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        }
    }

def parse(String description)
    {
    def map = createEvent(zwaveEvent(zwave.parse(description, [0x40:2, 0x43:2, 0x31:3, 0x42:1, 0x20:1, 0x85: 2])))
    if (!map) return null
    def result = [map]
    if (map.isStateChange && map.name in ["heatingSetpoint","thermostatMode"])
        {
        def map2 = [name: "thermostatSetpoint", unit: getTemperatureScale()]
        if (map.name == "thermostatMode")
            {
            state.lastTriedMode = map.value
            map2.value = device.latestValue("heatingSetpoint")
            }
        else
            {
            def mode = device.latestValue("thermostatMode")
            if (txtEnable) log.info("THERMOSTAT, latest mode = ${mode}")
            if (map.name == "heatingSetpoint")
                {
                map2.value = map.value
                map2.unit = map.unit
                }
            }
        if (map2.value != null)
            {
            if (logEnable) log.debug("THERMOSTAT, adding setpoint event: $map")
            result << createEvent(map2)

            }
        }
     if (map.name == "heatingSetpoint")
        {
        sendEvent(name: "coolingSetpoint", value: map.value, unit: locationScale)
        sendEvent(name: "thermostatSetpoint", value: map.value, unit: locationScale)
        }
    if (logEnable) log.debug("Parse returned $result")
    result
    }

def refresh()
    {
    delayBetween(
        [
        zwave.thermostatOperatingStateV1.thermostatOperatingStateGet().format(),
        zwave.thermostatModeV2.thermostatModeGet().format(),
        zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1).format(),
        zwave.sensorMultilevelV3.sensorMultilevelGet().format(),
        ],
        100)
    }

def zwaveEvent(hubitat.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd)
    {
    def cmdScale = cmd.scale == 1 ? "F" : "C"
    def temp;
    float tempfloat;
    def map = [:]
    if (cmd.scaledValue >= 327)
        {
        map.value = "--"
        }
    else
        {
        temp = convertTemperatureIfNeeded(cmd.scaledValue, cmdScale, cmd.precision)
        tempfloat = (Math.round(temp.toFloat() * 2)) / 2
        map.value = tempfloat
        }
    map.unit = getTemperatureScale()
    map.displayed = false
    if (cmd.setpointType == 1)
        {
        map.name = "heatingSetpoint"
        }
    else
        {
        return [:]
        }
    state.size = cmd.size
    state.scale = cmd.scale
    state.precision = cmd.precision
    map
    }

def zwaveEvent(hubitat.zwave.commands.sensormultilevelv3.SensorMultilevelReport cmd)
    {
    def temp
    float tempfloat
    def format
    def map = [:]
    if (cmd.sensorType == 1)
        {
        map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmd.scale == 1 ? "F" : "C", cmd.precision)
        map.unit = getTemperatureScale()
        map.name = "temperature"
        temp = map.value
        switch (temp)
            {
            case "32765": //0x7FFD 
                map.value = "low"
            break
            case "32767": //0x7FFF
                map.value = "high"
            break
            case "-32768": //0x8000
                map.value = "--"
            break
            default:
                tempfloat = (Math.round(temp.toFloat() * 2)) / 2
                map.value = tempfloat
            }
        }
    else if (cmd.sensorType == 5)
        {
        map.value = cmd.scaledSensorValue
        map.unit = "%"
        map.name = "humidity"
        }
    map
    }

def zwaveEvent(hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport cmd)
    {
    def map = [:]
    switch (cmd.operatingState)
        {
        case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_IDLE:
            map.value = "idle"
        break
        case hubitat.zwave.commands.thermostatoperatingstatev1.ThermostatOperatingStateReport.OPERATING_STATE_HEATING:
            map.value = "heating"
        break
        }
    map.name = "thermostatOperatingState"   
    map
    }

def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeReport cmd)
    {
    def map = [:]
    if (logEnable) log.debug("${cmd.mode}")
    switch (cmd.mode)
        {
        case '1':
            map.value = "heat"
        break        
        case '11':
            map.value = "eco"
        break
        }
    map.name = "thermostatMode"
    map
    }

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd)
    {
    delayBetween([
        zwave.associationV1.associationRemove(groupingIdentifier:1, nodeId:0).format(),
        zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]).format(),
        poll()], 2300)
    }

def zwaveEvent(hubitat.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport cmd)
    {
    if (logEnable) log.debug("Zwave event received: $cmd")
    }

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd)
    {
    if (logEnable) log.debug("Zwave event received: $cmd")
    }

def zwaveEvent(hubitat.zwave.Command cmd)
    {
    log.warn("Unexpected zwave command $cmd")
    }

def configure()
    {
    sendEvent(name: "supportedThermostatFanModes", value: JsonOutput.toJson(["on"]))
    sendEvent(name: "supportedThermostatModes", value: JsonOutput.toJson(["heat", "eco"]))
    sendEvent(name: "thermostatFanMode", value: "on")
    refresh()
    quickSetHeat(device.currentValue("heatingSetpoint"))
    }

def quickSetHeat(degrees)
    {
    setHeatingSetpoint(degrees, 1000)
    }

def setHeatingSetpoint(degrees, delay = 100)
    {
    setHeatingSetpoint(degrees.toDouble(), delay)
    }

def setHeatingSetpoint(Double degrees, Integer delay = 100)
    {
    if (logEnable) log.debug("setHeatingSetpoint($degrees, $delay)")
    def deviceScale = state.scale ?: 1
    def deviceScaleString = deviceScale == 2 ? "C" : "F"
    def locationScale = getTemperatureScale()
    def p = (state.precision == null) ? 1 : state.precision
    def convertedDegrees
    if (locationScale == "C" && deviceScaleString == "F") convertedDegrees = celsiusToFahrenheit(degrees)
    else if (locationScale == "F" && deviceScaleString == "C") convertedDegrees = fahrenheitToCelsius(degrees)
    else convertedDegrees = degrees
    delayBetween([
        zwave.thermostatSetpointV2.thermostatSetpointSet(setpointType: 1, scale: deviceScale, precision: p, scaledValue: convertedDegrees).format(),
        zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1).format()
        ], delay)
    }

def increaseHeatSetpoint()
    {
    if (device.currentValue("thermostatMode") =="eco") return // eco setpoint not adjustable through zwave
    float currentSetpoint = device.currentValue("heatingSetpoint")
    def locationScale = getTemperatureScale()
    float maxSetpoint
    float step
    if (locationScale == "C")
        {adjustHeatSetpoint
        maxSetpoint = 30
        step = 0.5
        }
    else
        {
        maxSetpoint = 86
        step = 1
        }
    if (currentSetpoint < maxSetpoint)
        {
        currentSetpoint = currentSetpoint + step
        quickSetHeat(currentSetpoint)
        }
    }

def decreaseHeatSetpoint()
    {
    if (device.currentValue("thermostatMode") == "eco") return // eco setpoint not adjustable through zwave
    float currentSetpoint = device.currentValue("heatingSetpoint")
    def locationScale = getTemperatureScale()
    float minSetpoint
    float step
    if (locationScale == "C")
        {
        minSetpoint = 5
        step = 0.5
        }
    else
        {
        minSetpoint = 41
        step = 1
        }
    if (currentSetpoint > minSetpoint)
        {
        currentSetpoint = currentSetpoint - step
        quickSetHeat(currentSetpoint)
        }
    }

def adjustHeatSetpoint(amount)
    {
    currentSetpoint = device.currentValue("heatingSetpoint")
    newSetpoint = device.currentValue("heatingSetpoint") + amount
    if (newSetpoint > 30) newSetpoint = 30
    if (newSetpoint < 5) newSetpoint = 5
    quickSetHeat(newSetpoint)
    }

def switchMode()
    {
    if (device.currentValue("thermostatMode") == "heat")
        {
        eco()
        }
    else
        {
        heat()
        }
    }

def getModeMap()
    {[
    "heat": 1,
    "eco": 11,
    ]}

def setCoolingSetpoint(coolingSetpoint)
    {
    if (logEnable) log.debug("${device.displayName} does not support cool setpoint")
    }

def heat()
    {
    if (logEnable) log.debug("heat mode applied")
    delayBetween([
        zwave.thermostatModeV2.thermostatModeSet(mode: 1).format(),
        zwave.thermostatModeV2.thermostatModeGet().format()
        ], 1000)
    }

def off()
    {
    eco()
    }

def eco()
    {
    if (logEnable) log.debug("Eco mode applied")
    delayBetween([
        zwave.thermostatModeV2.thermostatModeSet(mode: 11).format(),
        zwave.thermostatModeV2.thermostatModeGet().format()
        ], 1000)
    }

def auto()
    {
    if (logEnable) log.debug("${device.displayName} does not support auto mode")
    }

def emergencyHeat()
    {
    if (logEnable) log.debug("${device.displayName} does not support emergency heat mode")
    }

def cool()
    {
    if (logEnable) log.debug("${device.displayName} does not support cool mode")
    }

def setThermostatMode(String value)
    {
    if (value == "eco")
        {
        eco()
        }
    else
        {
        heat()
        }
    }

def fanOn()
    {
    if (logEnable) log.debug("${device.displayName} does not support fan on")
    }

def fanAuto()
    {
    if (logEnable) log.debug("${device.displayName} does not support fan auto")
    }

def fanCirculate()
    {
    if (logEnable) log.debug("${device.displayName} does not support fan circulate")
    }

def setThermostatFanMode()
    {
    if (logEnable) log.debug("${device.displayName} does not support fan mode")
    }
