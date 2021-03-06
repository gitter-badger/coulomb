/*
Copyright 2017 Erik Erlandson

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package coulomb.unitops

import spire.math._

import coulomb.infra._
import coulomb.define._

/**
 * An implicit trait that supports compile-time temperature conversion, when possible.
 * Also used to support addition, subtraction and comparisons.
 * This implicit will not exist if U1 and U2 are not convertable to one another.
 * @tparam N1 the numeric type of the temperature value
 * @tparam U1 the unit expresion type of the temperature
 * @tparam N2 numeric type of another temperature value
 * @tparam U2 unit expression type of the other temperature
 */
trait TempConverter[N1, U1, N2, U2] {
  /** the `Numeric` implicit for temperature numeric type N1 */
  def n1: Numeric[N1]
  /** the `Numeric` implicit for temperature numeric type N2 */
  def n2: Numeric[N2]
  /** a conversion from temperature value with type `(N1,U1)` to type `(N2,U2)` */
  def cv12(v: N1): N2
  /** a conversion from temperature value with type `(N2,U2)` to type `(N1,U1)` */
  def cv21(v: N2): N1
}
trait TempConverterDefaultPriority {
  // this default rule should work well everywhere but may be overridden for efficiency
  implicit def evidence[N1, U1, N2, U2](implicit
      t1: DerivedTemp[U1], t2: DerivedTemp[U2],
      nn1: Numeric[N1], nn2: Numeric[N2]): TempConverter[N1, U1, N2, U2] = {
    val coef = t1.coef / t2.coef
    new TempConverter[N1, U1, N2, U2] {
      val n1 = nn1
      val n2 = nn2
      def cv12(v: N1): N2 = {
        n2.fromType[Rational](((n1.toType[Rational](v) + t1.off) * coef) - t2.off)
      }
      def cv21(v: N2): N1 = {
        n1.fromType[Rational](((n2.toType[Rational](v) + t2.off) / coef) - t1.off)
      }
    }
  }
}
object TempConverter extends TempConverterDefaultPriority {
  // override the default temp-converter generation here for specific cases
}
