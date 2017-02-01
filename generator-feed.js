/*
 * The values we fed in last time.
 */
User.history = null;

/*
 * Increasing temperature/humidity values. Tuples with timespamp (in millis) and values for sensor.
 * The simulation interpolates the values between the specified intervals.
 */
User.upOne = [
   1000, [ 10,  0 ],
   2000, [ 15, 20 ],
   3000, [ 20, 40 ],
   4000, [ 25, 60 ],
   5000, [ 30, 80 ],
   6000, [ 35, 100 ]
];
User.upTwo = [
   1000, [  5,  0 ],
   2000, [ 10, 10 ],
   3000, [ 15, 30 ],
   4000, [ 20, 50 ],
   5000, [ 25, 70 ],
   6000, [ 30, 90 ]
];

/*
 * Decreasing temperature/humidity values. Tuples with timespamp (in millis) and values for sensor.
 * The simulation interpolates the values between the specified intervals.
 */
User.downOne = [
   1000, [ 35, 100 ],
   2000, [ 30, 80 ],
   3000, [ 25, 60 ],
   4000, [ 20, 40 ],
   5000, [ 15, 20 ],
   6000, [ 10,  0 ]
];
User.downTwo = [
   1000, [ 30, 90 ],
   2000, [ 25, 70 ],
   3000, [ 20, 50 ],
   4000, [ 15, 30 ],
   5000, [ 10, 10 ],
   6000, [  5,  0 ]
];

/*
 * The generator is called whenever new data is required.
 * @param mote    The Saguaro.Mote instance.
 * @param device  The device feeder object.
 */
User.generatorOne = function(/** Sonoran.Mote */mote, /** Saguaro.Device */device, /** Number */nanos){
    printf("Feeder: mote %s, time %s\n", mote, Util.nanos2str(nanos));
   if (User.history == null) {
      /* First time called! */
      User.history = User.upOne;
      return User.history;
   }

   /* Alternate between the two temperature vectors. */
   var e = User.history[User.history.length-1];
   if (e[0] == 6000) {
      User.history = User.downOne;
   } else {
      User.history = User.upOne;
   }

   /* Return the vector. */
   return User.history;
};

User.generatorTwo = function(/** Sonoran.Mote */mote, /** Saguaro.Device */device, /** Number */nanos){
   printf("Feeder: mote %s, time %s\n", mote, Util.nanos2str(nanos));
   if (User.history == null) {
      /* First time called! */
      User.history = User.upTwo;
      return User.history;
   }

   /* Alternate between the two temperature vectors. */
   var e = User.history[User.history.length-1];
   if (e[0] == 6000) {
      User.history = User.downTwo;
   } else {
      User.history = User.upTwo;
   }

   /* Return the vector. */
   return User.history;
};
