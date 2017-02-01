package gateway;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

public class Gateway {

    private static class Thermostat {
        private int shortAddress;
        private int currentTemperature;

        Thermostat(int shortAddress, int currentTemperature) {
            this.shortAddress = shortAddress;
            this.currentTemperature = currentTemperature;
        }

        int getShortAddress() {
            return shortAddress;
        }

        int getCurrentTemperature() {
            return currentTemperature;
        }

        void setCurrentTemperature(int currentTemperature) {
            this.currentTemperature = currentTemperature;
        }
    }

    //  Receiving temperatures
    private static int PAYLOAD = 9; // start byte of payload in radio frame
    private static int SOURCE_ADDRESS = 7; // start byte of source address in radio frame
    private static Radio radio = new Radio();
    private static Thermostat[] thermostats;
    private static int averageTemperature;

    //  Blinking LEDs
    private static int LOWER_THRESHOLD = 10;
    private static int HIGHER_THRESHOLD = 25;
    private static long BLINK_INTERVAL;
    private static Timer blinkTimer;
    private static int numberOfLEDs;
    private static byte blinkingLEDIndex;

    static {
        //  ------------------------------------------------
        //  Initialize constants
        BLINK_INTERVAL = Time.toTickSpan(Time.MILLISECS, 500);

        //  ------------------------------------------------
        //  Set up blinking of LEDs
        blinkingLEDIndex = (byte)0;
        numberOfLEDs = LED.getNumLEDs();
        //  turn off all LEDs initially
        allLedsOff();

        blinkTimer = new Timer();
        blinkTimer.setCallback(new TimerEvent(null){
            public void invoke(byte param, long time){
                Gateway.blink(param, time);
            }
        });
        //  set a new alarm in 500ms from now
        blinkTimer.setAlarmBySpan(BLINK_INTERVAL);

        //  ------------------------------------------------
        //  Set up radio reception
        radio = new Radio();
        // Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        //  Set the PAN ID to 0x22 and the short address to the last two bytes of the extended address
        //  PAN identifies network, extended and short address identify mote
        radio.setPanId(0x22, true);
        byte[] myAddrBytes = new byte[8];
        Mote.getParam(Mote.EUI64, myAddrBytes, 0);
        radio.setShortAddr(Util.get16le(myAddrBytes, 0));

        //  Put radio into receive mode for a long time on channel 0
        radio.setRxHandler(new DevCallback(null) {
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return Gateway.onRxPDU(flags, data, len, info, time);
            }
        });
        radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);
    }

    private static void allLedsOff () {
        for (byte i = 0; i< numberOfLEDs; i++)
            LED.setState(i, (byte)0);
    }

    private static void blink (byte param, long time) {
        byte oldIndex = blinkingLEDIndex;

        if (averageTemperature < LOWER_THRESHOLD) {
            blinkingLEDIndex = (byte)0; // yellow LED
        } else if (averageTemperature > HIGHER_THRESHOLD) {
            blinkingLEDIndex = (byte)2; // red LED
        } else {
            blinkingLEDIndex = (byte)1; // green LED
        }

        //  If the index has changed we need to turn off the old LED
        if (oldIndex != blinkingLEDIndex) {
            LED.setState(oldIndex, (byte)0);
        }

        //  Switch the LED state
        if (LED.getState(blinkingLEDIndex) == 1) {
            LED.setState(blinkingLEDIndex, (byte)0);
        } else {
            LED.setState(blinkingLEDIndex, (byte)1);
        }

        //  Setup a new timer alarm
        blinkTimer.setAlarmBySpan(BLINK_INTERVAL);
    }

    //  On a received pdu get the temperature, calculate the average
    //  and turn on the appropriate LEDs based on calculated average
    private static int onRxPDU(int flags, byte[] data, int length, int info, long time) {
        if (data == null) { // marks end of reception period
            // re-enable reception for a very long time
            radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);
            return 0;
        }

        logReceivedPackage(data, length);

        int temperature = getTemperatureFromData(data);

        handleReceivedTemperature(Util.get16le(data, SOURCE_ADDRESS), temperature);

        averageTemperature = calculateAverageTemperature(thermostats);
        return 0;
    }

    private static void addThermostat(int shortAddress, int temperature) {
        if (thermostats == null) {
            thermostats = new Thermostat[0];
        }

        // create new array with length + 1
        Thermostat[] newThermostats = new Thermostat[thermostats.length + 1];
        Util.copyData(thermostats, 0, newThermostats, 0, thermostats.length);
        thermostats = newThermostats;
        thermostats[thermostats.length - 1] = new Thermostat(shortAddress, temperature);

        Logger.appendString(csr.s2b("Added "));
        Logger.appendInt(thermostats.length);
        Logger.appendString(csr.s2b(". thermostat: "));
        Logger.appendInt(shortAddress);
        Logger.appendString(csr.s2b(", "));
        Logger.appendInt(temperature);
        Logger.appendString(csr.s2b("C"));
        Logger.flush(Mote.INFO);
    }

    private static void updateThermostatTemperature(int shortAddress, int temperature) {
        for (int i = 0; i < thermostats.length; i++) {
            if (thermostats[i].getShortAddress() == shortAddress) {
                thermostats[i].setCurrentTemperature(temperature);

                Logger.appendString(csr.s2b("Updated thermostat: "));
                Logger.appendInt(shortAddress);
                Logger.appendString(csr.s2b(" to "));
                Logger.appendInt(temperature);
                Logger.appendString(csr.s2b("C"));
                Logger.flush(Mote.INFO);
            }
        }
    }

    private static void handleReceivedTemperature(int shortAddress, int temperature) {
        if (thermostats == null) {
            addThermostat(shortAddress, temperature);
        } else if (thermostats.length == 1 && thermostats[0].getShortAddress() != shortAddress) {
            addThermostat(shortAddress, temperature);
        } else if (thermostats.length == 2) {
            updateThermostatTemperature(shortAddress, temperature);
        } else {
            Logger.appendString(csr.s2b("ERROR!!! thermostats array has invalid length"));
            Logger.flush(Mote.ERROR);
            throw new IndexOutOfRangeException();
        }
    }

    private static void logReceivedPackage(byte[] data, int length) {
        Logger.appendString(csr.s2b("Received data: "));
        Logger.appendString(csr.s2b("0x"));
        for (int i = 0; i < length; i++) {
            Logger.appendHexByte(data[i]);
        }
        Logger.flush(Mote.INFO);
    }

    private static int getTemperatureFromData(byte[] data) {
        // bytes 7-8 are the 14-bit, big-endian temperature value
        //int value = ((data[9] & 0xff) << 8) | (data[10] & 0xff);
        int temperature = Util.get16be(data, PAYLOAD);

        Logger.appendString(csr.s2b("Received temperature: "));
        Logger.appendInt(temperature);
        Logger.flush(Mote.INFO);

        return temperature;
    }

    private static int calculateAverageTemperature(Thermostat[] thermostats) {
        if (thermostats == null || thermostats.length == 0 || thermostats.length > 2) {
            return 0;
        } else if (thermostats.length == 1) {
            return thermostats[0].getCurrentTemperature();
        } else {
            int temperature1 = thermostats[0].getCurrentTemperature();
            int temperature2 = thermostats[1].getCurrentTemperature();
            int summe = temperature1 + temperature2;
            int average = summe >> 1;

            Logger.appendString(csr.s2b("Average temperature: ("));
            Logger.appendInt(temperature1);
            Logger.appendString(csr.s2b(" + "));
            Logger.appendInt(temperature2);
            Logger.appendString(csr.s2b(") / 2 = "));
            Logger.appendInt(summe);
            Logger.appendString(csr.s2b(" / 2 = "));
            Logger.appendInt(average);
            Logger.flush(Mote.INFO);

            return average;
        }
    }
}
