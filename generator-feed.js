/*
 * The values we fed in last time.
 */
User.history = null;

/*
 * The generator is called whenever new data is required.
 * @param mote    The Saguaro.Mote instance.
 * @param device  The device feeder object.
 */
User.randomTemperature = function(/** Sonoran.Mote */mote, /** Saguaro.Device */device, /** Number */nanos) {
   printf("Feeder: mote %s, time %s\n", mote, Util.nanos2str(nanos));

   /*
    * Increasing temperature/humidity values. Tuples with timespamp (in millis) and values for sensor.
    * The simulation interpolates the values between the specified intervals.
    */
   User.history = [
      1000, [ getRandomTemperature(), getRandomHumidity() ]
   ];

   return User.history;
};

/*
 * Helper methods
 */
function getRandomTemperature() {
   return getRandomInt(0, 45);
}

function getRandomHumidity() {
   return getRandomInt(0, 100);
}

function getRandomInt(min, max) {
   min = Math.ceil(min);
   max = Math.floor(max);
   return Math.floor(Math.random() * (max - min)) + min;
}
