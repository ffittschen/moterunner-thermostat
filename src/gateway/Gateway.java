package gateway;

import com.ibm.saguaro.system.*;
import com.ibm.saguaro.logger.*;

public class Gateway {

    private static int tempOfNode1;
    private static int tempOfNode2;

    static Radio radio = new Radio();

    static {
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

    // On a received pdu turn on the appropriate LEDs based on sequence number
    private static int onRxPDU(int flags, byte[] data, int len, int info, long time) {
        if (data == null) { // marks end of reception period
            // re-enable reception for a very long time
            radio.startRx(Device.ASAP, 0, Time.currentTicks() + 0x7FFFFFFF);
            return 0;
        }

        Logger.appendString(csr.s2b("Received data: "));
        Logger.appendInt((int) data[2]);
        Logger.flush(Mote.INFO);
        return 0;
    }

    private static int calculateAverageTemperature(byte temp1, byte temp2) {
        return 0;
    }
}
