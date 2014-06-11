metadata {

    definition (name: "Everspring Temperature Sensor", namespace: "chetstone", author: "Chester Wood") {
		capability "Battery"
		capability "Relative Humidity Measurement"
		capability "Temperature Measurement"
        capability "Configuration"
	   	capability "Sensor"

		fingerprint deviceId: "0x2101",	inClusters: "0x31 0x60 0x86 0x72 0x85 0x84 0x80 0x70 0x20 0x71"
    }	
	

	simulator {
		// messages the device returns in response to commands it receives

		for (int i = 0; i <= 100; i += 20) {
			status "temperature ${i}F": new physicalgraph.zwave.Zwave().sensorMultilevelV2.sensorMultilevelReport(
				scaledSensorValue: i, precision: 1, sensorType: 1, scale: 1).incomingMessage()
		}

		for (int i = 0; i <= 100; i += 20) {
			status "humidity ${i}%": new physicalgraph.zwave.Zwave().sensorMultilevelV2.sensorMultilevelReport(
				scaledSensorValue: i, precision: 0, sensorType: 5).incomingMessage()
		}

		for (int i = 0; i <= 100; i += 20) {
			status "battery ${i}%": new physicalgraph.zwave.Zwave().batteryV1.batteryReport(
				batteryLevel: i).incomingMessage()
		}
	}

	tiles {
		valueTile("temperature", "device.temperature", inactiveLabel: false) {
			state "temperature", label:'${currentValue}°',
			backgroundColors:[
				[value: 31, color: "#153591"],
				[value: 44, color: "#1e9cbb"],
				[value: 59, color: "#90d2a7"],
				[value: 74, color: "#44b621"],
				[value: 84, color: "#f1d801"],
				[value: 95, color: "#d04e00"],
				[value: 96, color: "#bc2323"]
			]
		}
		valueTile("humidity", "device.humidity", inactiveLabel: false) {
			state "humidity", label:'${currentValue}% humidity', unit:""
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
			state "battery", label:'${currentValue}% battery', unit:""
		}
		standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}

		main(["temperature", "humidity"])
		details(["temperature", "humidity", "battery", "configure"])
	}
}

// Parse incoming device messages to generate events
def parse(String description)
{
	def result = []
	def cmd = zwave.parse(description, [0x31: 2, 0x85: 2, 0x84: 1, 0x70: 1, 0x71: 1, 0x60: 3])
	if (cmd) {
		// if( cmd.CMD == "8407" ) { result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpNoMoreInformation().format()) }
		result << createEvent(zwaveEvent(cmd))
	}
	log.debug "Parse returned ${result}"
	return result
}

// Event Generation
def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
	log.debug "Woke UP"
	def result = []
    result << [descriptionText: "${device.displayName} woke up", displayed: false]
   /* result << response(zwave.batteryV1.batteryGet())
    result << response("delay 1200")
    result << response(zwave.configurationV1.configurationSet(parameterNumber: 6, size: 2, scaledConfigurationValue: 2).format())
    result << response("delay 1200")
    result << response(zwave.wakeUpV1.wakeUpNoMoreInformation())*/
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv2.SensorMultilevelReport cmd)
{
	def map = [:]
	switch (cmd.sensorType) {
		case 1:
			// temperature
			def cmdScale = cmd.scale == 1 ? "F" : "C"
            log.debug "Scale: ${cmd.scale}, Precision: ${cmd.precision}, Size: ${cmd.size}"
            log.debug "scaledSensorValue: ${cmd.scaledSensorValue}, sensorValue: ${cmd.sensorValue}"
			map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
			map.unit = getTemperatureScale()
			map.name = "temperature"
            log.debug "Value: ${map.value}, Unit: ${map.unit}"
			break;
		case 5:
			// humidity
			map.value = cmd.scaledSensorValue.toInteger().toString()
			map.unit = "%"
			map.name = "humidity"
			break;
        default: 
        	log.debug "fallthrough ${cmd.sensorType}"
            break;
	}
	map
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [:]
	map.name = "battery"
	map.value = cmd.batteryLevel > 0 ? cmd.batteryLevel.toString() : 1
	map.unit = "%"
	map.displayed = false
	map
}


def zwaveEvent(physicalgraph.zwave.commands.alarmv1.AlarmReport cmd) {
	if (cmd.alarmLevel == 1 && cmd.alarmType == 2) // Power ON
    {
    	log.debug "POWER ON"
    	configure();
        return [descriptionText: "${device.displayName} Power On", displayed: false]
    }
}


def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "Catchall reached for cmd: ${cmd.toString()}}"
	[:]
}

def configure() {
	log.debug "Sending Configuration"
	delayBetween([
		// send all data (temperature, humidity) every 2 minutes
		zwave.configurationV1.configurationSet(parameterNumber: 6, size: 2, scaledConfigurationValue: 8).format()
	])
}
