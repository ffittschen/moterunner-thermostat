package thermostat;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;
import com.ibm.iris.*;

public class ThermostatNode {

    //  Temperature simulation
    private static int MOTE_PORT = 6; // Link port on Mote (8 bit)
    private static int UDP_PAYLOAD = 7; // Payload size
    private static byte[] buffer; // The header information for LIP message plus context info
    private static byte sourcePort; // AsmId, i.e. port of our assembly
    private static boolean listenerAttached; // True if we have a process on the host which is picking up packets

    //  Temperature sensing
    private static long INTERVAL; // Time interval in ticks for sensor read
    private static SDev temperatureSensor = new SDev(); // Temperature/humidity sensor

    //  Transmission to gateway
    private static int RADIO_PAYLOAD = 9; // Payload size
    private static byte[] radioFrame; // Radio Frame for IEEE 802.15.4 MAC Layer
    private static int sequenceNumber;
    private static Radio radio = new Radio();

    static {
        //  ------------------------------------------------
        //  Initialize constants
        INTERVAL = Time.toTickSpan(Time.SECONDS, 1);

        //  ------------------------------------------------
        //  Set up temperature simulation
        Assembly.setDataHandler(new DataHandler(null) {
            @Override
            public int invoke(int info, byte[] data, int len) {
                return ThermostatNode.handleLIP(info, data, len);
            }
        });
        
        //  Allocate a buffer for outgoing UDP header data and fill in source port.
        buffer = new byte[UDP_PAYLOAD+4];
        sourcePort = Assembly.getActiveAsmId();
        Util.set16be(buffer, MOTE_PORT, sourcePort);

        //  Not yet attached
        listenerAttached = false;

        //  ------------------------------------------------
        //  Set up radio transmission
        radio = new Radio();
        //  Open the default radio
        radio.open(Radio.DID, null, 0, 0);
        //  Set the PAN ID to 0x22 and the short address to the last two bytes of the extended address
        //  PAN identifies network, extended and short address identify mote
        radio.setPanId(0x22, true);
        byte[] myAddrBytes = new byte[8];
        Mote.getParam(Mote.EUI64, myAddrBytes, 0);
        int shortAddr = Util.get16le(myAddrBytes, 0);
        radio.setShortAddr(shortAddr);

        // Radio frame layout
        //   1     1  |   1   |    2   |    0    |    2   |    2    |       0      |      ...      |  # of bytes per field
        //   0     1  |   2   |  3-4   |    0    |  5-6   |  7-8    |       0      |       9+      |  byte # in data array
        //+-----+-----+-------+--------+---------+--------+---------+--------------+---------------+
        //| FCF | FCA | SEQNO | DSTPAN | DSTADDR | SRCPAN | SRCADDR | aux.security |    payload    |  field name
        //+-----+-----+-------+--------+---------+--------+---------+--------------+---------------+
        //|<-----------------addressing fields--------------------->|
        radioFrame = new byte[RADIO_PAYLOAD+4];
        radioFrame[0] = Radio.FCF_DATA;
        radioFrame[1] = Radio.FCA_SRC_SADDR;
        Util.set16le(radioFrame, 3, 0x22); // Set DSTPAN
        Util.set16le(radioFrame, 5, 0x1234); // Set SRCPAN
        Util.set16le(radioFrame, 7, shortAddr); // Set SRCADDR
    }

    //  LIP callback on incoming UDP frames, i.e. incoming simulated temperature values
    private static int handleLIP(int info, byte[] data, int len) {
        //  Any message from the host will just record the sender address
        //  and record it as destination address where to sent data to.
        Util.copyData(data, 0, buffer, 0, MOTE_PORT); // Copy data of length MOTE_PORT into buffer
        Util.set16be(data, UDP_PAYLOAD+0, sourcePort); // aka. data[UDP_PAYLOAD] = sourcePort

        if (listenerAttached == false) {
            listenerAttached = true;
            try {
                temperatureSensor = new SDev();
                temperatureSensor.setReadHandler(new DevCallback(null) {
                    @Override
                    public int invoke(int flags, byte[] data, int len, int info, long time) {
                        return ThermostatNode.onTemperatureReading(flags, data, len, info, time);
                    }
                });
                temperatureSensor.open(IRIS.DID_MTS400_HUMID_TEMP, null, 0, 0);
                temperatureSensor.read(Device.ASAP, 4, 0);
            } catch (MoteException e) {
                // Some exception occured while opening the sensor
                Logger.appendString(csr.s2b("Caught exception: "));
                Logger.appendInt(e.reason);
                Logger.flush(Mote.ERROR);
                LED.setState(IRIS.LED_YELLOW, (byte) 1);
            }
        }

        return UDP_PAYLOAD+4;
    }

    //  Callback on sensor read
    private static int onTemperatureReading(int dflags, byte[] ddata, int dlen, int dinfo, long dtime) {
        if (dlen != 4 || ((dflags & Device.FLAG_FAILED) != 0)) {
            LED.setState(IRIS.LED_RED, (byte) 1);
        } else {
            LED.setState(IRIS.LED_RED, (byte) 0);
        }

        //  send data out
        Util.copyData(ddata, 0, buffer, UDP_PAYLOAD, dlen);
        LIP.send(buffer,0,UDP_PAYLOAD+dlen);

        sendToGateway(ddata, dlen);

        //  Read again in the future
        temperatureSensor.read(Device.TIMED, 4, dtime + INTERVAL);
        return 0;
    }

    private static void sendToGateway(byte[] data, int length) {
        //  set sequence number
        radioFrame[2] = (byte) sequenceNumber;
        //  set payload (Copy data of length len to position RADIO_PAYLOAD of radioFrame)
        Util.copyData(data, 0, radioFrame, RADIO_PAYLOAD, length);
        //  send the message
        radio.transmit(Device.ASAP | Radio.TXMODE_CCA, radioFrame, 0, RADIO_PAYLOAD+length, 0);
        logSentPackage(radioFrame, RADIO_PAYLOAD+length);
        sequenceNumber++;
    }

    private static void logSentPackage(byte[] data, int length) {
        Logger.appendString(csr.s2b("Sent data: "));
        Logger.appendString(csr.s2b("0x"));
        for (int i = 0; i < length; i++) {
            Logger.appendHexByte(data[i]);
        }
        Logger.flush(Mote.INFO);
    }
}
