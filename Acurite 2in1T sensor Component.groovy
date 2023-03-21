metadata
    {
    definition(name: "Acurite 2in1T Component sensor", namespace: "ymerj", author: "Yves Mercier", importUrl: "")
        {
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Pressure Measurement"
        capability "Relative Humidity Measurement"
        capability "Battery"
        capability "Refresh"
        }
    preferences
        {
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
        }
    }

void updated()
    {
    log.warn "updated"
    log.warn "description logging is: ${txtEnable == true}"
    }

void installed()
    {
    log.warn "Installed"
    refresh()
    }

void parse(String description)
    {
    log.warn "parse(String description) not implemented"
    }

void parse(List<Map> description)
    {
    description.each
        {
        if (it.name in ["humidity"])
            {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
            }
        if (it.name in ["temperature"])
            {
            if (txtEnable) log.info it.descriptionText
            sendEvent(it)
            }
        }
    }

void refresh()
    {
    parent?.componentRefresh(this.device)
    }
    
