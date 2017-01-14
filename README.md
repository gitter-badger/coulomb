### coulomb
A statically typed unit analysis library for Scala

`coulomb` is still under construction, but I hope to have a consumable release soon.  Feel free to play with it in the mean time! I'll keep the head of `develop` branch unbroken.

#### Why name it `coulomb` ?
`coulomb` is a library for "static units", and 'coulomb' is the "unit of static" (aka charge).

### examples

```scala
scala> import com.manyangled.coulomb._; import ChurchInt._; import SIBaseUnits._; import SIPrefixes._; import DemoUnits._; import extensions._
import com.manyangled.coulomb._
import ChurchInt._
import SIBaseUnits._
import SIPrefixes._
import DemoUnits._
import extensions._

scala> 1.withUnit[Liter].as[Meter <^> _3].str
res1: String = 0.001 meter ^ 3

scala> (1.withUnit[Foot] + 1.withUnit[Yard]).str
res2: String = 4.0 foot

scala> (1.withUnit[Foot] + 1.withUnit[Meter]).str
res3: String = 4.2808398950131235 foot

scala> (1000.withUnit[Meter] + 1.withUnit[Kilo <-> Meter]).str
res4: String = 2000.0 meter

scala> 5280.withUnit[Foot].as[Kilo <-> Meter].str
res5: String = 1.609344 kilo-meter

scala> (2.withUnit[Meter] * 2.withUnit[Meter <^> _2]).str
res6: String = 4.0 meter ^ 3

scala> (2.withUnit[Meter] * 2.withUnit[Meter <^> _2]).as[Liter].str
res8: String = 4000.0 liter

scala> (2.withUnit[Meter] * 2.withUnit[Meter <^> _2]).as[Kilo <-> Liter].str
res9: String = 4.0 kilo-liter

scala> (100.withUnit[Meter] / 9.withUnit[Second]).str
res10: String = 11.11111111111111 (meter ^ 1) * (second ^ -1)

scala> (11.withUnit[Meter </> Second] * 1.withUnit[Minute]).as[Yard].str
res11: String = 721.7847769028872 yard

scala> 1.withUnit[EarthGravity].as[Foot </> (Second <^> _2)].str
res12: String = 32.175196850393704 foot / (second ^ 2)

scala> (1.withUnit[Yard] / 1.withUnit[Foot]).str
res13: String = 3.0 unitless
```
