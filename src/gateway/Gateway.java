package gateway;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

public class Gateway {

    private static class Thermostat {
        private int shortAddr;
        private int currentTemp;

        public Thermostat(int shortAddr) {
            this.shortAddr = shortAddr;
        }

        public int getShortAddr() {
            return shortAddr;
        }

        public int getCurrentTemp() {
            return currentTemp;
        }

        public void setCurrentTemp(int currentTemp) {
            this.currentTemp = currentTemp;
        }
    }

    private static Timer blinkTimer;
    private static long BLINK_INTERVAL;
    private static int numLeds;
    private static byte idx;
    private static int LOW_THRESHOLD;
    private static int HIGH_THRESHOLD;
    private static Thermostat[] thermostats;
    private static int averageTemp;

    static Radio radio = new Radio();

    static {
        LOW_THRESHOLD = 10;
        HIGH_THRESHOLD = 25;
        BLINK_INTERVAL = Time.toTickSpan(Time.MILLISECS, 500);
        idx = (byte)1;
        // Get number of LEDs
        numLeds = LED.getNumLEDs();

        // turn off all LEDs initially
        allLedsOff();

        // create a new timer object
        blinkTimer = new Timer();
        blinkTimer.setCallback(new TimerEvent(null){
            public void invoke(byte param, long time){
                Gateway.blink(param, time);
            }
        });
        // set a new alarm in 500ms from now
        blinkTimer.setAlarmBySpan(BLINK_INTERVAL);

        // Create a new radio device
        radio = new Radio();
        // Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        // Set the PAN ID to 0x22 and the short address to the last two bytes of the extended address
        // PAN identifies network, extended and short address identify mote
        radio.setPanId(0x22, true);
        byte[] myAddrBytes = new byte[8];
        Mote.getParam(Mote.EUI64, myAddrBytes, 0);
        radio.setShortAddr(Util.get16le(myAddrBytes, 0));

        // Put radio into receive mode for a long time on channel 0
        radio.setRxHandler(new DevCallback(null) {
            public int invoke(int flags, byte[] data, int len, int info, long time) {
                return Gateway.onRxPDU(flags, data, len, info, time);
            }
        });
        radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);
    }

    private static void allLedsOff () {
        for (byte i=0; i<numLeds; i++)
            LED.setState(i, (byte)0);
    }

    private static void blink (byte param, long time) {
        if (LED.getState(idx) == 1)
            LED.setState(idx, (byte)0);
        else
            LED.setState(idx, (byte)1);

        if (averageTemp < LOW_THRESHOLD) {
            idx = (byte)0;
        } else if (averageTemp > HIGH_THRESHOLD) {
            idx = (byte)2;
        }

        // Setup a new timer alarm
        blinkTimer.setAlarmBySpan(BLINK_INTERVAL);
    }

    // On a received pdu turn on the appropriate LEDs based on sequence number
    private static int onRxPDU(int flags, byte[] data, int len, int info, long time) {
        if (data == null) { // marks end of reception period
            // re-enable reception for a very long time
            radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);
            return 0;
        }

        int temp = getTempFromData(data);
        Logger.appendString(csr.s2b("Received temp: "));
        Logger.appendInt(temp);
        Logger.flush(Mote.INFO);

        addOrEditThermostat(Util.get16le(data, 7), temp);

        averageTemp = calculateAverageTemperature(thermostats);
        Logger.appendString(csr.s2b("Average temperature: "));
        Logger.appendInt(averageTemp);
        Logger.flush(Mote.INFO);
        return 0;
    }

    private static void addOrEditThermostat(int shortAddr, int temperature) {
        if (thermostats == null) {
            thermostats = new Thermostat[1];

            Thermostat newThermostat = new Thermostat(shortAddr);
            newThermostat.setCurrentTemp(temperature);
            thermostats[0] = newThermostat;

            Logger.appendString(csr.s2b("Created a new thermostat"));
            Logger.flush(Mote.INFO);
        } else if (thermostats.length == 1) {
            if (thermostats[0].getShortAddr() == shortAddr) {
                thermostats[0].setCurrentTemp(temperature);
                Logger.appendString(csr.s2b("Found thermostat with shortAddr: "));
                Logger.appendInt(shortAddr);
                Logger.appendString(csr.s2b(" and set temperature to: "));
                Logger.appendInt(temperature);
                Logger.flush(Mote.INFO);
            } else {
                // create new array with length + 1
                Thermostat[] buffer = new Thermostat[thermostats.length + 1];
                Util.copyData(thermostats, 0, buffer, 0, thermostats.length);
                thermostats = buffer;

                Thermostat newThermostat = new Thermostat(shortAddr);
                newThermostat.setCurrentTemp(temperature);
                thermostats[thermostats.length - 1] = newThermostat;

                Logger.appendString(csr.s2b("Created a new thermostat"));
                Logger.flush(Mote.INFO);
            }
        } else if (thermostats.length == 2) {
            for (Thermostat thermostat: thermostats) {
                if (thermostat.getShortAddr() == shortAddr) {
                    thermostat.setCurrentTemp(temperature);
                    Logger.appendString(csr.s2b("Found thermostat with shortAddr: "));
                    Logger.appendInt(shortAddr);
                    Logger.appendString(csr.s2b(" and set temperature to: "));
                    Logger.appendInt(temperature);
                    Logger.flush(Mote.INFO);
                    return;
                }
            }

            Logger.appendString(csr.s2b("ERROR: Should not reach this point!"));
            Logger.flush(Mote.ERROR);
        } else {
            Logger.appendString(csr.s2b("ERROR!!! addOrEditThermostat failed"));
            Logger.flush(Mote.ERROR);
        }
    }

    private static int getTempFromData(byte[] data) {

        // bytes 7-8 are the 14-bit, big-endian temperature value
        Logger.appendString(csr.s2b("Raw value: 0x"));
        Logger.appendHexByte(data[9]);
        Logger.appendString(csr.s2b(", 0x"));
        Logger.appendHexByte(data[10]);
        Logger.flush(Mote.INFO);

        int value = ((data[9] & 0xff) << 8) | (data[10] & 0xff);

        return value;
    }

    private static int calculateAverageTemperature(Thermostat[] thermostats) {
        if (thermostats == null || thermostats.length == 0 || thermostats.length > 2) {
            return 0;
        } else if (thermostats.length == 1) {
            return thermostats[0].getCurrentTemp();
        } else {
            int summe = (thermostats[0].getCurrentTemp() + thermostats[1].getCurrentTemp());
            int average = summe >> 1;
            Logger.appendString(csr.s2b("Summe: "));
            Logger.appendInt(summe);
            Logger.appendString(csr.s2b("Durchschnitt: "));
            Logger.appendInt(average);
            Logger.flush(Mote.INFO);

            return average;
        }
    }
}
