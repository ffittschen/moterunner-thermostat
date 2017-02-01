SOCK1 = {
   /**                                                   
    * Evaluate arguments for a 'socket-send' and act accordingly. 
    * @param dstport  Destination port specified by user  
    * @param dstmotes Destination motes specified in command       
    * @param argv     String[] specified by user after dstport on the command line
    * @returns message to be sent to given mote or null if caller should handle this.
    */                                                     
   send: function(dstport, dstmote, argv) {
      if (argv.length != 1) {
         throw "SOCK1: invalid number of parameters";
      }
      var cmd = argv[0];
      if (cmd != 'attach') {
         throw "SOCK1: expecting 'attach'";
      }
      // Send any data to attach this socket to the tempsensor application.
      return Formatter.pack("u", [ 0 ]);
   },

   
   /**                                                   
    * Evaluate arguments for a 'socket-broadcast' and act accordingly. 
    * @param dstmote  The mote which does the broadcast. Must have a gateway installed and running.
    * @param dstport  Destination port on all motes to broadcast to. 
    * @param argv     String[] specified by user after dstport on the command line.
    * @returns message to be sent to given mote or null if caller should handle this.
    */                                                     
   broadcast: function(dstmote, dstport, argv) {
      println("SOCK1-broadcast: called..");
   },

   
   /**
    * Data has been received for this socket.
    * @param blob  Message with properties src (Sonoran.Mote), srcport (Number), dstport (Number)
    *              and data (Binary String), a Sonoran.MediaEvent
    * @returns a String to be loged to a file or stdout by caller
    */
   onData: function(blob) {
       var srcmote = blob.src;
       var srcport = blob.srcport;
       var data = blob.data;
       if (data.length != 4) {
           return "SOCK1 invalid data: " + srcmote.getUniqueid() + ", " + srcport + ", " + Formatter.binToHex(data) + "\n";
       }
       var arr = Formatter.unpack("UU", data);
       var t = arr[0];
       var h = arr[1];
       return sprintf("SOCK1 received temperature/humidity: %s: %H %d/%d\n", srcmote, data, t, h);
   },

   
   /** Called when this socket is closed. */
   onClose: function(status) {
      println("SOCK1-onClose: called..");
   }
};
