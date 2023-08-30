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
    definition(name: "HADB shortcut button", namespace: "ymerj", author: "Yves Mercier", importUrl: "")
        {
//        capability "Actuator"
        capability "Health Check"
        capability "PushableButton"
        capability "DoubleTapableButton"
        capability "HoldableButton"
        capability "ReleasableButton"
        }
    preferences
        {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        }
    attribute "received", "string"
    attribute "healthStatus", "enum", ["offline", "online"]
    }

void updated()
    {
    log.info "Updated..."
    log.warn "description logging is ${txtEnable == true}"
    sendEvent(name: "numberOfButtons", value: 1)
    }

void installed()
    {
    log.info "Installed..."
    device.updateSetting("txtEnable",[type:"bool",value:true])
    updated()
    }

void parse(String description) { log.warn "parse(String description) not implemented" }

void parse(List description)
    {
    description.each
        {
        if (it.name in ["unknown"])
            {
            if (it.value)
                {
                sendEvent(name: "received", value: it.value)
                if (txtEnable) log.info it.descriptionText
                }
            switch (it.value)
                {
                case "on":
                    push(1)
                break
                case "off":
                    doubleTap(1)
                break
                case "brightness_move_up":
                    hold(1)
                break
                case "brightness_stop":
                    release(1)
                break
                }
            }
        if (it.name in ["healthStatus"])
            {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
            }
        }
    }

def push(nb)
    {
    sendEvent(name: "pushed", value: nb, isStateChange: true)
    }

def hold(nb)
    {
    sendEvent(name: "held", value: nb, isStateChange: true)
    }

def doubleTap(nb)
    {
    sendEvent(name: "doubleTapped", value: nb, isStateChange: true)
    }

def release(nb)
    {
    sendEvent(name: "released", value: nb, isStateChange: true)
    }

def ping()
    {
    log.warn "ping not implemented"
    }
