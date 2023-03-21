/**
 * AcuRite Weather Station
 *
 *  David LaPorte
 *  Based on this helpful thread:
 *    https://community.smartthings.com/t/my-very-quick-and-dirty-integration-with-myacurite-smart-hub-st-webcore/97749
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
 *  Last Update 01/31/2022
 *
 *
 *  v0.0.1 - initial release
 *  v0.0.2 - modification by Yves Mercier to add support for multiple sensors
 *
 */

metadata
    {
    definition(name: "AcuRite Weather Station Parent", namespace: "ymerj", author: "Yves Mercier")
        {
        capability "Actuator"
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Pressure Measurement"
        capability "Relative Humidity Measurement"
        capability "Initialize"
        capability "Polling"
        capability "Battery"

        command "deleteAllChildDevices"

        attribute "device_battery_level", "string"
        attribute "temperature", "decimal"
        attribute "humidity", "decimal"
        attribute "pressure", "decimal"
        attribute "wind_speed_average", "number"
        attribute "windSpeed", "number"
        }

    preferences()
        {
        input name: "acurite_username", type: "text", required: true, title: "AcuRite Username"
        input name: "acurite_password", type: "text", required: true, title: "AcuRite Password"
        input name: "device_id", type: "text", required: true, title: "Device ID", description: "Your Device ID can be found looking for 'hubs' in the Network section of Chrome's Developer Tools while loading the MyAcurite dashboard"
        input name: "poll_interval", type: "enum", title: "Poll Interval:", required: false, options: [["5":"5 Minutes"], ["10":"10 Minutes"], ["15":"15 Minutes"], ["30":"30 Minutes"], ["60":"1 Hour"], ["180":"3 Hours"]], defaultValue: "5"
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        }
    }

def installed()
    {
    log.warn "installed"
    initialize()
    }

def updated()
    {
    log.warn "updated"
    initialize()
    }

def initialize()
    {
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    unschedule()
    if (logEnable) runIn(1800,logsOff)
    if (!acurite_username || !acurite_password || !device_id)
        {
        log.error "AcuRite: required fields not completed.  Please complete for proper operation."
        return
        }
    if(poll_interval == "5") runEvery5Minutes(poll)
    if(poll_interval == "10") runEvery10Minutes(poll)
    if(poll_interval == "15") runEvery15Minutes(poll)
    if(poll_interval == "30") runEvery30Minutes(poll)
    if(poll_interval == "60") runEvery1Hour(poll)
    if(poll_interval == "180") runEvery3Hours(poll)
    if (logEnable) log.debug "scheduled to run every ${poll_interval} minutes"
    }

def uninstalled()
    {
    log.warn "uninstalled"
    unschedule()
    deleteAllChildDevices()
    }

def poll()
    {
    if (txtEnable) log.info "poll() called"
    get_acurite_data()
    }

def get_acurite_data()
    {
    def login_params = [uri: "https://marapi.myacurite.com", path: "/users/login", body: ["remember": true, "email": "${acurite_username}", "password": "${acurite_password}"]]
    if (logEnable) log.debug(login_params)
    try
        {
        httpPostJson(login_params)
            {
            login_resp ->
            def token_id = login_resp.data.token_id
            def account_id = login_resp.data.user.account_users[0].account_id
            def data_params = [uri: "https://marapi.myacurite.com", path: "/accounts/${account_id}/dashboard/hubs/${device_id}", headers: ["x-one-vue-token": "${token_id}"]]
            if (logEnable) log.debug(data_params)
            try
                {
                httpGet(data_params)
                    {
                    data_resp ->
                    if (logEnable) log.debug "AcuRite: data response status: ${data_resp.status}"
                    if (logEnable) log.debug "data: ${data_resp.data}"
                    def data = data_resp.data
                    for (int i in 1..5)
                        {
                        entity = "${data.devices[i].model_code}-${i}"
                        friendly = data.devices[i].name
                        mapping = [deviceType: "Acurite ${data.devices[i].model_code} Component sensor", event: [[name: "humidity", value: data.devices[i].sensors[1].last_reading_value as int, descriptionText:"${friendly} humidity is ${data.devices[i].sensors[1].last_reading_value}"],[name: "temperature", value: data.devices[i].sensors[0].last_reading_value as int, descriptionText:"${friendly} temperature is ${data.devices[i].sensors[0].last_reading_value}"]]]
                        updateChildDevice(mapping, entity, friendly)
                        }
                    battery = 0
                    if (data.devices[0].battery_level == "Normal") battery = 100
                    description = "${data.devices[0].name} battery level is ${battery}"
                    if (txtEnable) log.info description
                    sendEvent(name: "battery", value: battery, descriptionText: description)
                    description = "${data.devices[0].name} temperature is ${data.devices[0].sensors[0].last_reading_value}"
                    if (txtEnable) log.info description
                    sendEvent(name: "temperature", value: data.devices[0].sensors[0].last_reading_value.toInteger(), unit: data.devices[0].sensors[0].chart_unit, descriptionText: description)
                    description = "${data.devices[0].name} humidity is ${data.devices[0].sensors[1].last_reading_value}"
                    if (txtEnable) log.info description
                    sendEvent(name: "humidity", value: data.devices[0].sensors[1].last_reading_value, unit: data.devices[0].sensors[1].chart_unit, descriptionText: description)
                    description = "${data.devices[0].name}  wind speed is ${data.devices[0].sensors[2].last_reading_value}"
                    if (txtEnable) log.info description
                    sendEvent(name: "windSpeed", value: data.devices[0].sensors[2].last_reading_value, unit: data.devices[0].sensors[2].chart_unit, descriptionText: description)
                    description = "${data.devices[0].name} pressure is ${data.devices[0].sensors[5].last_reading_value}"
                    if (txtEnable) log.info description
                    sendEvent(name: "pressure", value: data.devices[0].sensors[5].last_reading_value, unit: data.devices[0].sensors[5].chart_unit, descriptionText: description)
                    description = "${data.devices[0].name} average wind speed is ${data.devices[0].sensors[6].last_reading_value}"
                    if (txtEnable) log.info description
                    sendEvent(name: "wind_speed_average", value: data.devices[0].sensors[6].last_reading_value, unit: data.devices[0].sensors[6].chart_unit, descriptionText: description)
                    }
                }
            catch (groovyx.net.http.HttpResponseException e2) {log.error "AcuRite: data failed: " + e2.response.status + ": " + e2.response.data}
            }
        }
    catch (groovyx.net.http.HttpResponseException e1) {log.error "AcuRite: login failed: " + e1.response.status}
    }

def updateChildDevice(mapping, entity, friendly)
    {
    def ch = createChild(mapping.deviceType, entity, friendly)
    if (!ch)
        {
        log.warn "Child type: ${mapping.deviceType} not created for entity: ${entity}"
        return
        }
    else
        {
        ch.parse(mapping.event)
        }
    }

def createChild(deviceType, entity, friendly)
    {
    def ch = getChildDevice("${device.id}-${entity}")
    if (!ch) ch = addChildDevice("ymerj", deviceType, "${device.id}-${entity}", [name: entity, label: friendly, isComponent: false])
    return ch
    }

def componentRefresh()
    {
    log.warn "Child refresh not implemented"
    }

def deleteAllChildDevices()
    {
    log.warn "uninstalling all child devices"
    getChildDevices().each {deleteChildDevice(it.deviceNetworkId)}
    }

def logsOff()
    {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
    }
