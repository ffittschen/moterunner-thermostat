package thermostat;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;
import com.ibm.iris.*;

public class ThermostatNode {

    // timer objects
    private static Timer temperatureTimer;
    private static Timer sendTimer;
    // time interval in ticks for reading the temperature
    private static long QUERY_INTERVAL;
    // time interval in ticks for sending the temperature after starting to read it
    private static long TRANSMIT_INTERVAL;

    private static byte[] xmit;
    private static int currentTemp;

    static Radio radio = new Radio();
    static SDev tempSensor = new SDev();

    static {
        // Initialize constants
        QUERY_INTERVAL = Time.toTickSpan(Time.SECONDS, 1);
        TRANSMIT_INTERVAL = Time.toTickSpan(Time.MILLISECS, 700);

        // ---------- Temp Sensor ----------
        try {
            tempSensor.open(IRIS.DID_MTS400_HUMID_TEMP, null, 0, 0);
            tempSensor.setReadHandler(new DevCallback(null){
                public int invoke (int flags, byte[] data, int len, int info, long time) {
                    return ThermostatNode.setTempFromReading(flags, data, len, info, time);
                }
            });
        } catch (MoteException e) {
            // Some exception occured while opening the sensor
            Logger.appendString(csr.s2b("Caught exception: "));
            Logger.appendInt(e.reason);
            Logger.flush(Mote.ERROR);
        }

        // ---------- Radio ----------

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

        // Prepare beacon frame with source addressing
        xmit = new byte[7];
        xmit[0] = Radio.FCF_BEACON;
        xmit[1] = Radio.FCA_SRC_SADDR;
        Util.set16le(xmit, 3, 0x22);
        Util.set16le(xmit, 5, 0x1234);

        // Setup a periodic timer callback for reading the temperature
        temperatureTimer = new Timer();
        temperatureTimer.setCallback(new TimerEvent(null) {
            public void invoke(byte param, long time) {
                ThermostatNode.readTemperature(param, time);
            }
        });

        sendTimer = new Timer();
        sendTimer.setCallback(new TimerEvent(null) {
            public void invoke(byte param, long time) {
                ThermostatNode.sendTemperature(param, time);
            }
        });

        // Start the timer
        temperatureTimer.setAlarmBySpan(QUERY_INTERVAL);
    }

    // Called on a temperatureTimer alarm
    public static void readTemperature(byte param, long time) {
        tempSensor.read(Device.ASAP, 4, time);

        // Setup a new alarm
        sendTimer.setAlarmBySpan(TRANSMIT_INTERVAL);
    }

    // Called on a sendTimer alarm
    public static void sendTemperature(byte param, long time) {
        // set sequence number
        xmit[2] = (byte) currentTemp;
        // send the message
        radio.transmit(Device.ASAP | Radio.TXMODE_CCA, xmit, 0, 7, 0);
        // Setup a new alarm
        temperatureTimer.setAlarmBySpan(QUERY_INTERVAL);
    }

    public static int setTempFromReading(int flags, byte[] data, int len, int info, long time) {
        Logger.appendString(csr.s2b("Flags: "));
        Logger.appendInt(flags);
        Logger.flush(Mote.INFO);

        Logger.appendString(csr.s2b("Length: "));
        Logger.appendInt(len);
        Logger.flush(Mote.INFO);

        Logger.appendString(csr.s2b("Info: "));
        Logger.appendInt(info);
        Logger.flush(Mote.INFO);

        Logger.appendString(csr.s2b("Time: "));
        Logger.appendLong(time);
        Logger.flush(Mote.INFO);

        // bytes 2-3 are the 14-bit, big-endian temperature value
        Logger.appendString(csr.s2b("Raw value: 0x"));
        Logger.appendHexByte(data[2]);
        Logger.appendString(csr.s2b(", 0x"));
        Logger.appendHexByte(data[3]);
        Logger.flush(Mote.INFO);

        int value = ((data[2] & 0xff) << 8) | (data[3] & 0xff);
        Logger.appendString(csr.s2b("Int value: "));
        Logger.appendInt(value);
        Logger.flush(Mote.INFO);

        int tempInCelsius = value / 100 - 40; // (int) (value * 0.01 - 40);
        Logger.appendString(csr.s2b("Temperature in Celsius: "));
        Logger.appendInt(tempInCelsius);
        Logger.flush(Mote.INFO);

        currentTemp = tempInCelsius;

        return 0;
    }
}
