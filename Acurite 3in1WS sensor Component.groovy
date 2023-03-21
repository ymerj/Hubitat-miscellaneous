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

1.0 - Yves Mercier - initial version

*/

metadata
{
    definition(name: "Acurite 3in1WS Component sensor", namespace: "ymerj", author: "Yves Mercier", importUrl: "")
    {
    capability "Sensor"
    capability "Temperature Measurement"
    capability "Pressure Measurement"
    capability "Relative Humidity Measurement"
    capability "Battery"
    capability "Refresh"
    }
    preferences {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

void updated() {
    log.info "Updated..."
    log.warn "description logging is: ${txtEnable == true}"
}

void installed() {
    log.info "Installed..."
    device.updateSetting("txtEnable",[type:"bool",value:true])
    refresh()
}

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List<Map> description) {
    description.each {
        if (it.name in ["humidity"]) {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
        }
        if (it.name in ["temperature"]) {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
        }
         if (it.name in ["pressure"]) {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
        }
        if (it.name in ["wind"]) {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
        }
    }
}

void refresh() {
    parent?.componentRefresh(this.device)
}
