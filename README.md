# IBM Mote Runner Thermostat Simulation
In the *Ausgewählte Themen aus dem Bereich Software Engineering - Internet of Things and Services (IN3050)* course of the winter term 2016/2017 at TUM, we had a project where we should create a thermostat simulation using [IBM Mote Runner](https://www.zurich.ibm.com/moterunner/).

## Problem Statement / Task
Consider a network containing two nodes and a central gateway. In specific time intervals, the nodes measure the temperature of the environment and send it to the gateway. The gateway calculates the average of these two temperatures. If the average is less than a specific amount (lower threshold) the left LED or the yellow one starts blinking. If the average temperature is more than a specific amount (upper threshold) the right LED or the red one starts blinking. Otherwise (the average temperature is between two thresholds) the middle LED or the green one starts blinking. LEDs on the nodes can be turned on and off whenever they send data to the gateway.

- Set the thresholds and the time intervals for sensing and blinking as you want.
- Be creative and add more interesting features!

## Getting Started
Assuming you have downloaded and installed Mote Runner, there are only a few more steps to start this simulation:

1. Check out this repository
2. Open a terminal and navigate to the root folder of this repository
3. Run `mrsh` to start the Mote Runner Shell
4. Inside the Mote Runner Shell, call `install.mrsh`

The `install.mrsh` script will build the assemblies, create motes, load all necessary assemblies onto the motes and run the simulation. You can then navigate to [http://localhost:5000/dashboard/dashboard.html] in you browser, to observe the LEDs or to take a look at the log messages in the console.

## Design decisions
Since we only worked with the simulator and therefore did not have any real sensors, I somehow had to simulate temperature changes, so that calculating an average and making differently colored LEDs blink according to that average makes sense.

To do so, I used the feeder API, which will generate random numbers in a reasonable range and feed them into the `MTS400_HUMID_TEMP` sensor device.