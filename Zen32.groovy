/*
 * ===================== Zooz Scene Controller (ZEN32) Driver =====================
 *
 *  Copyright 2022 Robert Morris
 *  
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
 * =======================================================================================
 * 
 *  Changelog:
 *  v2.0    (2022-02-20): Add Indicator command class support (thanks to @jtp10181); requires ZEN32 firmware 10.10 or greater
 *  v1.0.1  (2021-04-23): Fix typo in BasicGet; pad firmware subversion with 0 as needed
 *  v1.0    (2021-04-01): Initial Release

 *  3.0     (2023-01-02): Modified by Yves Mercier to be used as multiple child switch
 *  3.0.1   (2023-07-01): Fix inverted value for state on power restore
*/
 
import groovy.transform.Field
 
@Field static final Map commandClassVersions = [
   0x20: 1,    // Basic
   0x25: 1,    // SwitchBinary
   0x55: 1,    // TransportService
   0x59: 1,    // AssociationGroupInfo
   0x5B: 1,    // CentralScene
   0x6C: 1,    // Supervision
   0x70: 1,    // Configuration
   0x72: 2,    // ManufacturerSpecific
   0x85: 1,    // Association
   0x86: 2,    // Version
   0x87: 3,    // Indicator
   0x8E: 2,    // MultichannelAssociation
   0x98: 1,    // Security
   0x9F: 1     // Security S2
]
 
// color name to parameter value mapping
@Field static Map<String,Integer> colorNameMap = ["white": 0, "blue": 1, "green": 2, "red": 3]
 
// LED/button number to parameter value mappings (for LED color parmeters):
@Field static Map<Integer,Integer> ledIndicatorParams = [1: 2, 2: 3, 3: 4, 4: 5, 5: 1]
@Field static Map<Integer,Integer> ledColorParams = [1: 7, 2: 8, 3: 9, 4: 10, 5: 6]
@Field static Map<Integer,Integer> ledBrightnessParams = [1: 12, 2: 13, 3: 14, 4: 15, 5: 11]
 
// LED number mappings for Indicator command class:
@Field static Map<Integer,Short> indicatorLEDNumberMap = [0:0x50, 5:0x43, 1:0x44, 2:0x45, 3:0x46, 4:0x47]
 
@Field static final Map zwaveParameters = [
   16: [input: [name: "param.16", type: "number", title: "[16] Automtically turn relay off after ... minutes (0=disable auto-off; default)", range: 0..65535], size: 4],
   17: [input: [name: "param.17", type: "number", title: "[17] Automtically turn relay on after ... minutes (0=disable auto-on; default)", range: 0..65535], size: 4],
   18: [input: [name: "param.18", type: "enum", title: "[18] State on power restore (relay and buttons)", options: [[1: "Off"], [2: "On"], [0: "Previous state (default)"]]], size: 1],
   19: [input: [name: "param.19", type: "enum", title: "[19] Local (phyiscal) and Z-Wave control/smart bulb mode", options: [[0: "Disable local control, enable Z-Wave"], [1: "Enable local and Z-Wave control (default)"], [2: "Disable local and Z-Wave control"]]], size: 1],
   20: [input: [name: "param.20", type: "enum", title: "[20] Behavior of relay reports if local and/or Z-Wave control disabled (scene/button events are always sent)", options: [[0:"Send on/off reports and change LED"],[1:"Do not send on/off reports or change LED (default)"]]], size: 1],
   21: [input: [name: "param.21", type: "enum", title: "[21] 3-way switch type", options: [[0:"Mechanical (connected 3-way turns on/off) (default)"],[1:"Momentary (connected 3-way toggles on/off)"]]], size: 1]
   ]

// LED indicator mode for relay and buttons 1-4:
@Field static final Map defaultZwaveParameters = [2: [value: 2, size: 1], 3: [value: 2, size: 1], 4: [value: 2, size: 1], 5: [value: 2, size: 1]]
 
metadata
    {
    definition (name: "Zooz MultiSwitch Controller (ZEN32)", namespace: "ymerj", author: "Robert Morris", importUrl: "https://raw.githubusercontent.com/ymerj/Hubitat-miscellaneous/main/Zen32.groovy")
        {
        capability "Actuator"
        capability "Switch"
        capability "Configuration"
        capability "PushableButton"
        // capability "HoldableButton"
        // capability "ReleasableButton"
 
        command "refresh"
        command "setConfigParameter", [[name:"Parameter Number*", type: "NUMBER"], [name:"Value*", type: "NUMBER"], [name:"Size*", type: "NUMBER"]]
        command "setLED",
            [
            [name:"ledNumber", type: "NUMBER", description: "LED/button number (1-5, 5=large/relay button)", constraints: 1..5],
            [name:"colorName", type: "ENUM", description: "Color name (white, blue, green, red)", constraints: ["white", "blue", "green", "red"]],
            [name:"brightness", type: "NUMBER", description: "Brightness level (100, 60, or 30%; will round to nearest; 0 for off)", constraints: [100,60,30,0]],
            ]
        command "setIndicator",
            [
            [name:"ledNumber*", type: "NUMBER", description: "LED/Button number (1-5, 5=large button, 0=all)", constraints: 0..5],
            [name:"mode*", type: "ENUM", description: "Mode (flash, on, or off)", constraints: ["flash", "on", "off"]],
            [name:"lengthOfOnOffPeriods", type: "NUMBER", description: "On/off period length in tenths of seconds (0-254, e.g., 10 = 1 second)", constraints: 2..255],
            [name:"numberOfOnOffPeriods", type: "NUMBER", description: "Number of total on/off periods (1-254), or 255 for indefinite", constraints: 1..255],
            [name:"lengthOfOnPeriod", type: "NUMBER", description: "On period length in tenths of seconds (e.g., 8 = 0.8 seconds; can be used to create asymmetric on/off periods)", constraints: 1..254],
            ]
        command "removeChild", [[name: "buttonNumber", type: "NUMBER"]]
           
        attribute "buttonLed_1", "enum", [ledColor, ledLevel]
        attribute "buttonLed_2", "enum", [ledColor, ledLevel]
        attribute "buttonLed_3", "enum", [ledColor, ledLevel]
        attribute "buttonLed_4", "enum", [ledColor, ledLevel]

        fingerprint mfr:"027A", prod:"7000", deviceId:"A008", inClusters:"0x5E,0x25,0x70,0x20,0x5B,0x85,0x8E,0x59,0x55,0x86,0x72,0x5A,0x73,0x87,0x9F,0x6C,0x7A" 
        fingerprint mfr:"027A", prod:"7000", model: "A008"
        }
 
    preferences
        {
        zwaveParameters.each {input it.value.input}
        input name: "relayLEDBehavior", type: "enum", title: "Relay LED behavior", options: [[0:"LED on when relay off, off when on (default)"],[1:"LED on when relay on, off when off"], [2:"LED on or off as modified by \"Set LED\" command (recommended in some use cases)"]]
        input name: "enableDebug", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "enableDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        }
    }
 
void logsOff()
    {
    log.warn "Disabling debug logging"
    device.updateSetting("enableDebug", [value:"false", type:"bool"])
    }
 
void parse(String description)
    {
    if (enableDebug) log.debug "parse description: ${description}"
    hubitat.zwave.Command cmd = zwave.parse(description, commandClassVersions)
    if (cmd) {zwaveEvent(cmd)}
    }
 
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd)
    {
    hubitat.zwave.Command encapCmd = cmd.encapsulatedCommand(commandClassVersions)
    if (encapCmd) {zwaveEvent(encapCmd)}
    }
 
void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd)
    {
    if (logEnable) log.debug "VersionReport: ${cmd}"
    device.updateDataValue("firmwareVersion", """${cmd.firmware0Version}.${String.format("%02d", cmd.firmware0SubVersion)}""")
    device.updateDataValue("protocolVersion", "${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
    device.updateDataValue("hardwareVersion", "${cmd.hardwareVersion}")
    }
 
void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.DeviceSpecificReport cmd)
    {
    if (enableDebug) log.debug "DeviceSpecificReport v2: ${cmd}"
    switch (cmd.deviceIdType)
        {
        case 1: // serial number
            String serialNumber = ""
            if (cmd.deviceIdDataFormat == 1) {cmd.deviceIdData.each {serialNumber += hubitat.helper.HexUtils.integerToHexString(it & 0xff, 1).padLeft(2, '0')}}
            else {cmd.deviceIdData.each {serialNumber += (char)it }}
            if (enableDebug) log.debug "Device serial number is $serialNumber"
            device.updateDataValue("serialNumber", serialNumber)
        break
        }
    }
 
void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd)
    {
    if (enableDebug) log.debug "ConfigurationReport: ${cmd}"
    if (enableDesc) log.info "${device.displayName} parameter '${cmd.parameterNumber}', size '${cmd.size}', is set to '${cmd.scaledConfigurationValue}'"
    }
 
void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd)
    {
    if (enableDebug) log.debug "BasicReport:  ${cmd}"
    String value = (cmd.value ? "on" : "off")
    if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
    sendEvent(name: "switch", value: value)
    }            
 
void zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd)
    {
    if (enableDebug) log.debug "BasicSet: ${cmd}"
    String value = (cmd.value ? "on" : "off")
    if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
    sendEvent(name: "switch", value: value)
    }
 
void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
    {
    if (enableDebug) log.debug "SwitchBinaryReport: ${cmd}"
    String value = (cmd.value ? "on" : "off")
    if (enableDesc && device.currentValue("switch") != value) log.info "${device.displayName} switch is ${value}"
    sendEvent(name: "switch", value: value)
    }
 
void zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd)
    {    
    if (enableDebug) log.debug "CentralSceneNotification: ${cmd}"
    Integer btnBaseNum = cmd.sceneNumber ?: 0
    Integer btnNum = btnBaseNum
    turnOnOff(btnNum)
    }
 
void zwaveEvent(hubitat.zwave.commands.indicatorv3.IndicatorReport cmd)
    {
    if (enableDebug) log.debug "IndicatorReport: ${cmd}"
    }
 
void zwaveEvent(hubitat.zwave.commands.indicatorv3.IndicatorSupportedReport cmd)
    {
    if (enableDebug) log.debug "IndicatorSupportedReport: ${cmd}"
    if (cmd.nextIndicatorId > 0) {sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(zwave.indicatorV3.indicatorSupportedGet(indicatorId:cmd.nextIndicatorId)), hubitat.device.Protocol.ZWAVE))}
    }
 
void zwaveEvent(hubitat.zwave.Command cmd)
    {
    if (enableDebug) log.debug "skip: ${cmd}"
    }

def refresh()
    {
    if (enableDebug) log.debug "refresh"
    return delayBetween(
        [
        zwaveSecureEncap(zwave.basicV1.basicGet()),
        zwaveSecureEncap(zwave.configurationV1.configurationGet()),
        zwaveSecureEncap(zwave.versionV2.versionGet())
        ], 100)
    }
 
def on()
    {
    if (enableDebug) log.debug "on()"
    state.flashing = false
    zwaveSecureEncap(zwave.basicV1.basicSet(value: 0xFF))
    }
 
def off()
    {
    if (enableDebug) log.debug "off()"
    state.flashing = false
    zwaveSecureEncap(zwave.basicV1.basicSet(value: 0x00))
    }
 
void push(btnNum)
    {
    turnOnOff(btnNum)
    //sendEvent(name: "pushed", value: btnNum, isStateChange: true, type: "digital")
    }
 
void hold(btnNum)
    {
    //sendEvent(name: "held", value: btnNum, isStateChange: true, type: "digital")
    }
 
void release(btnNum)
    {
    //sendEvent(name: "released", value: btnNum, isStateChange: true, type: "digital")
    }

def uninstalled()
    {
    log.info("uninstalled...")
    unschedule()
    deleteAllChildDevices()
    }

void installed()
    {
    log.warn "Installed..."
    for (int i in 1..4)
        {
        createChild(i)
        setLED(i, "white", 0)
        }
    }
 
def configure()
    {
    log.warn "configure..."
    installed()
    List<String> cmds = []
    sendEvent(name: "numberOfButtons", value: 5)
    zwaveParameters.each
        { param, data ->
        if (settings[data.input.name] != null) 
            {
            if (enableDebug) log.debug "Preference parameter: setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
            cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size))
            }
        }
    defaultZwaveParameters.each
        { param, data ->
        if (enableDebug) log.debug "Default parameter: setting parameter $param (size:  ${data.size}) to ${data.value}"
        cmds <<zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: data.value as BigInteger, parameterNumber: param, size: data.size))
        }
    if (relayLEDBehavior != null)
        {
        BigInteger val = relayLEDBehavior as BigInteger
        cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: val, parameterNumber: 1, size: 1))
        }
    cmds << zwaveSecureEncap(zwave.versionV2.versionGet())
    cmds << zwaveSecureEncap(zwave.versionV2.versionGet())
    cmds << zwaveSecureEncap(zwave.configurationV1.configurationGet())
    return delayBetween(cmds, 150)
    }
 
def updated() // Apply preferences changes, including updating parameters
    {
    log.info "updated..."
    log.warn "Debug logging is: ${enableDebug == true ? 'enabled' : 'disabled'}"
    log.warn "Description logging is: ${enableDesc == true ? 'enabled' : 'disabled'}"
    if (enableDebug)
        {
        log.debug "Debug logging will be automatically disabled in 30 minutes..."
        runIn(1800, logsOff)
        }
    List<String> cmds = []   
    zwaveParameters.each
        { param, data ->
        if (settings[data.input.name] != null)
            {
            if (enableDebug) log.debug "Preference parameter: setting parameter $param (size:  ${data.size}) to ${settings[data.input.name]}"
            cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: settings[data.input.name] as BigInteger, parameterNumber: param, size: data.size))
            }
        }
        if (relayLEDBehavior != null)
            {
            BigInteger val = relayLEDBehavior as BigInteger
            cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: val, parameterNumber: 1, size: 1))
            }
        cmds << zwaveSecureEncap(zwave.versionV2.versionGet())
        cmds << zwaveSecureEncap(zwave.manufacturerSpecificV2.deviceSpecificGet(deviceIdType: 1))
        return delayBetween(cmds, 200)
    }

def setLED(Number ledNumber, String colorName, brightness)
    {
    if (enableDebug) log.debug "setLED(Number $ledNumber, String $colorName, Object $brightness)"
    Integer intColor = colorNameMap[colorName?.toLowerCase()] ?: 0
    Integer intLevel = 0
    switch (brightness as Integer)
        {
        case 1..44:
           intLevel = 2
           break
        case 45..74:
           intLevel = 1
           break
        default:
           intLevel = 0
        }
    List<String> cmds = []
    cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: intColor, parameterNumber: ledColorParams[ledNumber as Integer], size: 1))
    cmds << zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: intLevel, parameterNumber: ledBrightnessParams[ledNumber as Integer], size: 1))
    sendEvent(name: "buttonLed_${ledNumber}", value: [colorName, brightness])
    return delayBetween(cmds, 500)
    }
 
def setIndicator(Number ledNumber=0, String mode="on", Number lengthOfOnOffPeriods=null, Number numberOfOnOffPeriods=null, Number lengthOfOnPeriod=null)
    {
    if (enableDebug) log.debug "setIndicator($ledNumber, $mode, $lengthOfOnOffPeriods, $numberOfOnOffPeriods, $lengthOfOnPeriod)"
    Short indId = indicatorLEDNumberMap[ledNumber as Integer] ?: 0
    List<String> cmds = []
    if (mode.equalsIgnoreCase("flash"))
        {
        Short lenOnOff = lengthOfOnOffPeriods != null ? lengthOfOnOffPeriods as Short : 0
        Short numOnOff = numberOfOnOffPeriods != null ? numberOfOnOffPeriods as Short : 0
        Short lenOn = lengthOfOnPeriod != null ? lengthOfOnPeriod as Short : 0
        log.trace "lenOnOff = $lenOnOff, numOnOff=$numOnOff, lenOn=$lenOn, indId=$indId "
        cmds << zwaveSecureEncap(zwave.indicatorV3.indicatorSet(value: 0xFF, indicatorCount: 3, indicatorValues:
            [
            [indicatorId: indId, propertyId: 0x03, value: lenOnOff], // This property is used to set the duration (in tenth of seconds) of an on/off period
            [indicatorId: indId, propertyId: 0x04, value: numOnOff], // This property is used to set the number of on/off periods to run
            [indicatorId: indId, propertyId: 0x05, value: lenOn]     // This property is used to set the length of the on time during an on/off period; it allows asymmetric on/off  periods
            ]))
        }
    else
        {
        Short onOff = (mode.equalsIgnoreCase("on") ? 0xFF : 0x00)
        cmds << zwaveSecureEncap(zwave.indicatorV3.indicatorSet(value: 0xFF, indicatorCount: 1, indicatorValues: [[indicatorId: indId, propertyId: 0x02, value: onOff]]))
        }
    return delayBetween(cmds, 300)
    }
 
def setConfigParameter(number, value, size)
    {
    return zwaveSecureEncap(setParameter(number, value, size.toInteger()))
    }
 
def setParameter(number, value, size)
    {
    if (enableDebug) log.debug "setParameter(number: $number, value: $value, size: $size)"
    return zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: value.toInteger(), parameterNumber: number, size: size))
    }

def turnOnOff(btnNum)
    {
    if (btnNum == 5) return
    int paramNbr = btnNum.toInteger() + 1
    def ch = createChild(btnNum)
    def etat = ch.currentValue("switch")
    switch (etat)
        {
        case "off":
            ch.parse([[name:"switch", value:"on", type: "physical", descriptionText:"${ch.label} was turned on [physical]"]])
            sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: 3, parameterNumber: paramNbr, size: 1)), hubitat.device.Protocol.ZWAVE))
        break
        case "on":
            ch.parse([[name:"switch", value:"off", type: "physical", descriptionText:"${ch.label} was turned off [physical]"]])
            sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: 2, parameterNumber: paramNbr, size: 1)), hubitat.device.Protocol.ZWAVE))
        break
        }
    }

def componentRefresh(ch)
    {
    if (enableDesc) log.info "received refresh request from ${ch.label}"
    }

def componentOn(ch)
    {
    if (enableDesc) log.info "received on request from ${ch.label}"
    getChildDevice(ch.deviceNetworkId).parse([[name:"switch", value:"on", type: "digital", descriptionText:"${ch.label} was turned on [digital]"]])
    def btnNum = ch.deviceNetworkId[-1]
    int paramNbr = btnNum.toInteger() + 1
    sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: 3, parameterNumber: paramNbr, size: 1)), hubitat.device.Protocol.ZWAVE))
    }

def componentOff(ch)
    {
    if (enableDesc) log.info "received off request from ${ch.label}"
    getChildDevice(ch.deviceNetworkId).parse([[name:"switch", value:"off", type: "digital", descriptionText:"${ch.label} was turned off [digital]"]])
    def btnNum = ch.deviceNetworkId[-1]
    int paramNbr = btnNum.toInteger() + 1
    sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(zwave.configurationV1.configurationSet(scaledConfigurationValue: 2, parameterNumber: paramNbr, size: 1)), hubitat.device.Protocol.ZWAVE))
    }

def createChild(btnNum)
    {
    def ch = getChildDevice("${device.id}-${btnNum}")
    if (!ch) {ch = addChildDevice("hubitat", "Generic Component Switch", "${device.id}-${btnNum}", [name: "Button ${btnNum}", label: "Button ${btnNum}", isComponent: false])}
    return ch
    }

def removeChild(btnNum)
    {
    def ch = getChildDevice("${device.id}-${btnNum}")
    if (ch) {deleteChildDevice("${device.id}-${btnNum}")}
    }

def deleteAllChildDevices()
    {
    log.info "Uninstalling all Child Devices"
    getChildDevices().each {deleteChildDevice(it.deviceNetworkId)}
    }
