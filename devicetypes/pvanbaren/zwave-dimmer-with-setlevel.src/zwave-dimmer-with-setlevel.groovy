/**
 *  Z-Wave Dimmer with setLevel
 *
 *  Implements a deferred setLevel on a Z-Wave dimmer switch.
 *  If the setLevel() method is called while the switch is off,
 *  the light level change is deferred until the next on() method.
 *
 *  Copyright 2016 Philip Van Baren
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
 */
metadata
{
	definition (name: "Z-Wave Dimmer with setLevel", namespace: "pvanbaren", author: "Philip Van Baren")
	{
		capability "Actuator"
		capability "Refresh"
		capability "Switch"
		capability "Switch Level"

		// Switch Multilevel
		fingerprint inClusters: "0x26"
	}

	simulator
	{
		status "on":  "command: 2603, payload: FF"
		status "off": "command: 2603, payload: 00"
		status "09%": "command: 2603, payload: 09"
		status "10%": "command: 2603, payload: 0A"
		status "33%": "command: 2603, payload: 21"
		status "66%": "command: 2603, payload: 42"
		status "99%": "command: 2603, payload: 63"

		["FF", "00", "09", "0A", "21", "42", "63"].each
		{
			val ->
			reply "2001$val,delay 100,2602": "command: 2603, payload: $val"
		}
	}

	tiles
	{
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true)
		{
			state "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#79b821", nextState:"turningOff"
			state "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff", nextState:"turningOn"
			state "turningOn", label:'${name}', icon:"st.switches.switch.on", backgroundColor:"#79b821"
			state "turningOff", label:'${name}', icon:"st.switches.switch.off", backgroundColor:"#ffffff"
		}
		controlTile("levelSliderControl", "device.level", "slider", height: 1, width: 3, inactiveLabel: false)
		{
			state "level", action:"switch level.setLevel"
		}
		standardTile("refresh", "device.switch", inactiveLabel: false, decoration: "flat")
		{
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
	}

	main(["switch"])
	details(["switch", "levelSliderControl", "refresh"])
}

// parse events into attributes
def parse(String description)
{
	def result = null
	if (description != "updated")
	{
		def cmd = zwave.parse(description, [0x20: 1, 0x26: 3, 0x70: 1])
		if (cmd)
		{
			result = zWaveEvent(cmd)
	        log.debug("'$description' parsed to $result")
		}
		else
		{
			log.debug("Couldn't zwave.parse '$description'")
		}
	}
	result
}

def updated()
{
	response(refresh())
}

def zWaveEvent(physicalgraph.zwave.Command cmd)
{
	def result = []
	def value = (cmd.value ? "on" : "off")
	def switchEvent = createEvent(name: "switch", value: value, descriptionText: "$device.displayName was turned $value")
	result << switchEvent
	if (cmd.value)
	{
		result << createEvent(name: "level", value: cmd.value, unit: "%")
	}
	return result
}

def on()
{
	if (state.defaultLevel)
	{
		def level = state.defaultLevel
		state.defaultLevel = 0
		log.debug "Turning on using stored defaultLevel: ${level}%"    
		delayBetween([
			zwave.basicV1.basicSet(value: level).format(),
			zwave.switchMultilevelV1.switchMultilevelGet().format()
		], 5000)
	}
	else
	{
		log.debug "Turning On"
		delayBetween([
			zwave.basicV1.basicSet(value: 0xFF).format(),
			zwave.switchMultilevelV1.switchMultilevelGet().format(),
		], 5000)
	}
}

def off()
{
	log.debug "Turning Off"
	delayBetween([
		zwave.basicV1.basicSet(value: 0x00).format(),
		zwave.switchMultilevelV1.switchMultilevelGet().format(),
	], 5000)
}

def refresh()
{
	zwave.switchMultilevelV1.switchMultilevelGet().format()
}

def setLevel(level)
{
	if (level)
	{
		if (level > 99)
		{
			level = 99
		}

		if (device.currentValue('switch') == "on")
		{
			// If currently on, change the level
			log.debug "setLevel: ${level}%"
			delayBetween([
				zwave.basicV1.basicSet(value: level).format(),
				zwave.switchMultilevelV1.switchMultilevelGet().format()
			], 5000)
		}
		else
		{
			// If currently off, cache the level until the on command is received
			log.debug "Switch is off, storing requested ${level}% as defaultLevel."
			state.defaultLevel = level
		}
	}
	else
	{
		off()
	}
}
