/**
 *  Turn Off With Motion
 *
 *	Turn off one or more switches as soon as movement is detected,
 *  and then turn them back on after some amount of time has passed without movement.
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
definition(
	name: "Turn Off With Motion",
	namespace: "pvanbaren",
	author: "Philip Van Baren",
	description: "Turns off a switch when motion occurs, turns it back on after a timer expires",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Solution/switches.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Solution/switches@2x.png")

preferences
{
	section("Turn off switch(es)...")
	{
		input "theswitch", "capability.switch", multiple: true
	}
	section("when there is movement...")
	{
		input "themotion", "capability.motionSensor", title: "Where?", multiple: true
	}
	section("Turn back on after movement ceases for...")
	{
		input "thetime", "number", title: "Minutes?"
	}
}

def installed()
{
	subscribe(themotion, "motion.active", motionDetectedHandler)
	subscribe(themotion, "motion.inactive", motionStoppedHandler)
}

def updated()
{
	unsubscribe()
	installed()
}

def motionDetectedHandler(evt)
{
	log.debug "$evt.name: $evt.value"
	log.debug "Turning off the switch"

	theswitch.off()
	resetTimeout()
}

def motionStoppedHandler(evt)
{
	log.debug "$evt.name: $evt.value"
	log.debug "setting timeout"
	resetTimeout()
}

def checkMotion()
{
	def motionState = themotion.currentState("motion")

	// Check if any motion sensor is active
	if (motionState.contains("active"))
	{
		// Motion active; just log it and do nothing
		log.debug "Motion is active yet, resetting the timer"
		resetTimeout()
	}
	else
	{
		// Get the time elapsed between now and when the motion reported inactive
		// The elapsed time is in milliseconds, but comparisons are in seconds
		def elapsed = (now() - state.lastMotion) / 1000

		def threshold = 60 * thetime

		if (elapsed >= (threshold - 1))
		{
			log.debug "Motion inactive for $elapsed sec:  turning switch on"
			theswitch.on()
		}
		else
		{
			log.debug "Motion inactive for $elapsed sec"
			scheduleCheckMotion(threshold - elapsed)
		}
	}
}

private resetTimeout()
{
	state.lastMotion = now()
	scheduleCheckMotion(60 * thetime)
}

private scheduleCheckMotion(def inSeconds)
{
	// Try to turn off the lights after the timeout
	// but no sooner than 30 seconds from now
	def nextCheck = Math.max(30, inSeconds)
	runIn(nextCheck, checkMotion)
}
