/*
Copyright 2023
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-------------------------------------------
Change history:
0.1.52- Yves Mercier - initial version
0.1.59 - Yves Mercier - Change healthStatus handling
*/

metadata
    {
    definition(name: "HADB Component Unknown Sensor to Switch", namespace: "ymerj", author: "Yves Mercier", importUrl: "")
        {
        capability "Switch"
        capability "Refresh"
        // capability "Actuator"
        capability "Health Check"
        }
    preferences
        {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        }
    attribute "healthStatus", "enum", ["offline", "online"]
    }

void updated()
    {
    log.info "Updated..."
    log.warn "description logging is ${txtEnable == true}"
    }

void installed()
    {
    log.info "Installed..."
    device.updateSetting("txtEnable",[type:"bool",value:true])
    sendEvent(name: "switch", value: "off", description: "initial switch state")
    refresh()
    }

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List<Map> description)
    {
    description.each
        {
        if (it.name in ["healthStatus"])
            {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
            }
        if (it.name in ["unknown"])
            {
            if (txtEnable) log.info it.descriptionText
            sendEvent(name: "switch", value: it.value, description: it.descriptionText)
            }
        }
    }

void on()
    {
    log.warn "Sensor cannot be logically actuated"
    }

void off()
    {
    log.warn "Sensor cannot be logically actuated"
    }

void refresh()
    {
    parent?.componentRefresh(this.device)
    }

def ping()
    {
    refresh()
    }
