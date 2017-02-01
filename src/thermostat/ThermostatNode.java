package thermostat;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;
import com.ibm.iris.*;

public class ThermostatNode {

    private static int MOTE_PORT = 6; //  Link port on Mote (8 bit)
    private static int PAYLOAD   = 7; //  Payload size

    // The header information for LIP message plus context info
    private static byte[] buffer;

    // True if we have a process on the host which is picking up packets
    private static boolean listenerAttached;

    // time interval in ticks for device read
    private static long INTERVAL;

    // asmid, i.e. our port
    private static byte sourcePort;

    // temperature/humid device
    private static SDev tempSensor = new SDev();

    private static byte[] xmit;
    private static int sequenceNumber;
    private static Radio radio = new Radio();

    static {
        INTERVAL = Time.toTickSpan(Time.SECONDS, 1);

        Assembly.setDataHandler(new DataHandler(null) {
            @Override
            public int invoke(int info, byte[] data, int len) {
                return ThermostatNode.handleLIP(info, data, len);
            }
        });
        
        // Allocate a buffer for outgoing UDP header data and fill in source port.
        // Source IP address is automatically filled in by OS.
        //    LIPHDR    7    - required header by LIP protocol
        //    VALUE     2    - emp sensor value
        //
        buffer = new byte[PAYLOAD+4];
        sourcePort = Assembly.getActiveAsmId();
        Util.set16be(buffer, MOTE_PORT, sourcePort);

        // Not yet attached
        listenerAttached = false;

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
        int shortAddr = Util.get16le(myAddrBytes, 0);
        radio.setShortAddr(shortAddr);
        Logger.appendString(csr.s2b("ShortAddr: "));
        Logger.appendInt(shortAddr);
        Logger.flush(Mote.INFO);


        // Prepare frame with source addressing
        //   1     1  |   1   |    2   |    0    |    2   |    2    |       0      |      ...      |  # of bytes per field
        //   0     1  |   2   |  3-4   |    0    |  5-6   |  7-8    |       0      |       9+      |  byte # in data array
        //+-----+-----+-------+--------+---------+--------+---------+--------------+---------------+
        //| FCF | FCA | SEQNO | DSTPAN | DSTADDR | SRCPAN | SRCADDR | aux.security |    payload    |  field name
        //+-----+-----+-------+--------+---------+--------+---------+--------------+---------------+
        //|<-----------------addressing fields--------------------->|
        xmit = new byte[PAYLOAD+6];
        xmit[0] = Radio.FCF_DATA;
        xmit[1] = Radio.FCA_SRC_SADDR;
        Util.set16le(xmit, 3, 0x22); // Set DSTPAN
        Util.set16le(xmit, 5, 0x1234); // Set SRCPAN
        Util.set16le(xmit, 7, shortAddr); // Set SRCADDR
    }

    // LIP callback on incoming UDP frames.
    private static int handleLIP(int info, byte[] data, int len) {
        // Any message from the host will just record the sender address
        // and record it as destination address where to sent data to.
        Util.copyData(data, 0, buffer, 0, MOTE_PORT); // Copy data of length MOTE_PORT into buffer
        Util.set16be(data, PAYLOAD+0, sourcePort); // aka. data[PAYLOAD] = sourcePort

        if (listenerAttached == false) {
            listenerAttached = true;
            try {
                tempSensor = new SDev();
                tempSensor.setReadHandler(new DevCallback(null) {
                    @Override
                    public int invoke(int flags, byte[] data, int len, int info, long time) {
                        return ThermostatNode.onTempDone(flags, data, len, info, time);
                    }
                });
                tempSensor.open(IRIS.DID_MTS400_HUMID_TEMP, null, 0, 0);
                tempSensor.read(Device.ASAP, 4, 0);
            } catch (MoteException e) {
                // Some exception occured while opening the sensor
                Logger.appendString(csr.s2b("Caught exception: "));
                Logger.appendInt(e.reason);
                Logger.flush(Mote.ERROR);
                LED.setState(IRIS.LED_YELLOW, (byte) 1);
            }
        }

        return PAYLOAD+4;
    }

    // Callback on device read
    private static int onTempDone(int dflags, byte[] ddata, int dlen, int dinfo, long dtime) {
        if (dlen != 4 || ((dflags & Device.FLAG_FAILED) != 0)) {
            LED.setState(IRIS.LED_RED, (byte) 1);
        } else {
            LED.setState(IRIS.LED_RED, (byte) 0);
        }

        Logger.appendString(csr.s2b("Sending raw value: 0x"));
        Logger.appendHexByte(ddata[2]);
        Logger.appendString(csr.s2b(", 0x"));
        Logger.appendHexByte(ddata[3]);
        Logger.flush(Mote.INFO);

        // send data out
        Util.copyData(ddata, 0, buffer, PAYLOAD, dlen);
        LIP.send(buffer,0,PAYLOAD+dlen);

        sendToGateway(ddata, dlen);

        // Read again in the future
        tempSensor.read(Device.TIMED, 4, dtime + INTERVAL);
        return 0;
    }

    private static void sendToGateway(byte[] data, int len) {
        // set sequence number
        xmit[2] = (byte) sequenceNumber;
        // set payload (Copy data of length len to position 8 of xmit)
        Util.copyData(data, 0, xmit, PAYLOAD+2, len);
        // send the message
        radio.transmit(Device.ASAP | Radio.TXMODE_CCA, xmit, 0, PAYLOAD+len, 0);

        sequenceNumber++;
    }
}
