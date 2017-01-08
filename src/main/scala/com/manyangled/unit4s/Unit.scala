package com.manyangled.unit4s

import scala.language.experimental.macros
import scala.language.implicitConversions
import scala.annotation.implicitNotFound
import scala.language.higherKinds

import com.manyangled.church.{ Integer, IntegerValue }
import Integer.{ _0, _1 }

trait UnitExpr

trait FundamentalUnit extends UnitExpr

trait PrefixUnit extends UnitExpr

trait DerivedUnit[U <: UnitExpr] extends UnitExpr

sealed trait <*> [LUE <: UnitExpr, RUE <: UnitExpr] extends UnitExpr

sealed trait </> [LUE <: UnitExpr, RUE <: UnitExpr] extends UnitExpr

sealed trait <^> [UE <: UnitExpr, P <: Integer] extends UnitExpr

sealed trait <-> [PU <: PrefixUnit, UE <: UnitExpr] extends UnitExpr

// unitless values (any units have canceled)
sealed trait Unity extends UnitExpr

case class UnitRec[UE <: UnitExpr](name: String, coef: Double)

class UCompanion[U <: UnitExpr](uname: String, ucoef: Double) {
  def this(n: String) = this(n, 1.0)

  implicit val furec: UnitRec[U] = UnitRec[U](uname, ucoef)
}

@implicitNotFound("Implicit not found: CompatUnits[${U1}, ${U2}].\nIncompatible Unit Expressions: ${U1} and ${U2}")
case class CompatUnits[U1 <: UnitExpr, U2 <: UnitExpr](
  coef: Double // conversion factor from U1 to U2
)

object CompatUnits {
  implicit def witnessCompatUnits[U1 <: UnitExpr, U2 <: UnitExpr]: CompatUnits[U1, U2] =
    macro infra.UnitMacros.compatUnits[U1, U2]
}

case class UnitExprString[U <: UnitExpr](str: String)

object UnitExprString {
  implicit def witnessUnitExprString[U <: UnitExpr]: UnitExprString[U] =
    macro infra.UnitMacros.unitExprString[U]
}

class Unit[U <: UnitExpr](val value: Double)(implicit uesU: UnitExprString[U]) {
  def as[U2 <: UnitExpr](implicit cu: CompatUnits[U, U2], uesU2: UnitExprString[U2]): Unit[U2] =
    new Unit[U2](this.value * cu.coef)

  def +[U2 <: UnitExpr](that: Unit[U2])(implicit cu: CompatUnits[U2, U]): Unit[U] =
    new Unit[U](this.value + cu.coef * that.value)

  override def toString = s"$value ${uesU.str}"
}

object Unit {
  implicit class ExtendWithUnits[N](v: N)(implicit num: Numeric[N]) {
    def withUnit[U <: UnitExpr](implicit uesU: UnitExprString[U]): Unit[U] = new Unit[U](num.toDouble(v))
  }
}

object infra {
  import scala.language.experimental.macros
  import scala.reflect.macros.whitebox

  trait DummyU extends FundamentalUnit
  trait DummyP extends PrefixUnit

  class UnitMacros(val c: whitebox.Context) {
    import scala.reflect.runtime.currentMirror 
    import scala.tools.reflect.ToolBox 
    val toolbox = currentMirror.mkToolBox()

    import c.universe._

    def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    def typeName(tpe: Type): String = tpe.typeSymbol.fullName

    def evalTree[T](tree: Tree) = c.eval(c.Expr[T](c.untypecheck(tree.duplicate)))

    def superClass(tpe: Type, sup: Type): Option[Type] = {
      val supSym = sup.typeSymbol
      val bc = tpe.baseClasses.drop(1)
      if (bc.count { bSym => bSym == supSym } < 1) None else Some(tpe.baseType(supSym))
    }

    val ivalType = typeOf[IntegerValue[Integer._0]].typeConstructor
    val urecType = typeOf[UnitRec[DummyU]].typeConstructor

    val fuType = typeOf[FundamentalUnit]
    val puType = typeOf[PrefixUnit]
    val duType = typeOf[DerivedUnit[DummyU]].typeConstructor

    val mulType = typeOf[<*>[DummyU, DummyU]].typeConstructor
    val divType = typeOf[</>[DummyU, DummyU]].typeConstructor
    val powType = typeOf[<^>[DummyU, Integer._0]].typeConstructor
    val preType = typeOf[<->[DummyP, DummyU]].typeConstructor

    def intVal(intT: Type): Int = {
      val ivt = appliedType(ivalType, List(intT))
      val ival = c.inferImplicitValue(ivt, silent = false)
      evalTree[Int](q"${ival}.value")
    }
    
    def coefVal(unitT: Type): Double = {
      val urt = appliedType(urecType, List(unitT))
      val ur = c.inferImplicitValue(urt, silent = false)
      evalTree[Double](q"${ur}.coef")
    }

    def nameVal(unitT: Type): String = {
      val urt = appliedType(urecType, List(unitT))
      val ur = c.inferImplicitValue(urt, silent = false)
      evalTree[String](q"${ur}.name")
    }

    def urecVal(unitT: Type): (String, Double) = {
      val urt = appliedType(urecType, List(unitT))
      val ur = c.inferImplicitValue(urt, silent = false)
      evalTree[(String, Double)](q"(${ur}.name, ${ur}.coef)")
    }

    object MulOp {
      def unapply(tpe: Type): Option[(Type, Type)] = {
        if (tpe.typeConstructor =:= mulType) {
          val (lht :: rht :: Nil) = tpe.typeArgs
          Option(lht, rht)
        } else None
      }
    }

    object DivOp {
      def unapply(tpe: Type): Option[(Type, Type)] = {
        if (tpe.typeConstructor =:= divType) {
          val (lht :: rht :: Nil) = tpe.typeArgs
          Option(lht, rht)
        } else None
      }
    }

    object PowOp {
      def unapply(tpe: Type): Option[(Type, Int)] = {
        if (tpe.typeConstructor =:= powType) {
          val (bT :: expT :: Nil) = tpe.typeArgs
          Option(bT, intVal(expT))
        } else None
      }
    }

    object FUnit {
      def unapply(tpe: Type): Option[String] = {
        if (superClass(tpe, fuType).isEmpty) None else {
          val (name, _) = urecVal(tpe)
          Option(name)
        }
      }
    }

    object PreOp {
      def unapply(tpe: Type): Option[(String, Double, Type)] = {
        if (tpe.typeConstructor =:= preType) {
          val (pre :: uexp :: Nil) = tpe.typeArgs
          val pu = superClass(pre, puType)
          if (pu.isEmpty) None else {
            val (name, coef) = urecVal(pre)
            Option(name, coef, uexp)
          }
        } else None
      }
    }

    object DUnit {
      def unapply(tpe: Type): Option[(String, Double, Type)] = {
        val du = superClass(tpe, duType)
        if (du.isEmpty) None else {
          val (name, coef) = urecVal(tpe)
          Option(name, coef, du.get.typeArgs(0))
        }
      }
    }

    def canonical(typeU: Type): (Double, Map[Type, Int]) = {
      typeU.dealias match {
        case FUnit(_) => {
          (1.0, Map(typeU -> 1))
        }
        case DUnit(_, coef, dsub) => {
          val (dcoef, dmap) = canonical(dsub)
          (coef * dcoef, dmap)
        }
        case MulOp(lsub, rsub) => {
          val (lcoef, lmap) = canonical(lsub)
          val (rcoef, rmap) = canonical(rsub)
          val mmap = rmap.iterator.foldLeft(lmap) { case (m, (t, e)) =>
            if (m.contains(t)) {
              val ne = m(t) + e
              if (ne == 0) m else m + ((t, ne))
            } else {
              m + ((t, e))
            }
          }
          (lcoef * rcoef, mmap)
        }
        case DivOp(lsub, rsub) => {
          val (lcoef, lmap) = canonical(lsub)
          val (rcoef, rmap) = canonical(rsub)
          val dmap = rmap.iterator.foldLeft(lmap) { case (m, (t, e)) =>
            if (m.contains(t)) {
              val ne = m(t) - e
              if (ne == 0) m else m + ((t, ne))
            } else {
              m + ((t, -e))
            }
          }
          (lcoef / rcoef, dmap)
        }
        case PowOp(bsub, exp) => {
          val (bcoef, bmap) = canonical(bsub)
          if (exp == 0)
            (1.0, Map.empty[Type, Int])
          else
            (math.pow(bcoef, exp), bmap.mapValues(_ * exp))
        }
        case PreOp(_, coef, sub) => {
          val (scoef, smap) = canonical(sub)
          (coef * scoef, smap)
        }
        case _ => {
          // This should never execute
          abort(s"Undefined Unit Type: ${typeName(typeU)}")
          (0.0, Map.empty[Type, Int])
        }
      }
    }

    def compatUnits[U1: WeakTypeTag, U2: WeakTypeTag]: Tree = {
      val tpeU1 = weakTypeOf[U1]
      val tpeU2 = weakTypeOf[U2]

      val (coef1, map1) = canonical(tpeU1)
      val (coef2, map2) = canonical(tpeU2)

      // units are compatible if their canonical representations are equal
      val compat = (map1 == map2)

      if (!compat) q"" // fail implicit resolution if they aren't compatible
      else {
        // if they are compatible, then create the corresponding witness
        val cq = q"${coef1 / coef2}"
        q"""
          _root_.com.manyangled.unit4s.CompatUnits[$tpeU1, $tpeU2]($cq)
        """
      }
    }

    def ueAtomicString(typeU: Type): Boolean = {
      typeU.dealias match {
        case FUnit(_) => true
        case DUnit(_, _, _) => true
        case _ => false
      }
    }

    def ueString(typeU: Type): String = {
      typeU.dealias match {
        case FUnit(name) => name
        case DUnit(name, _, _) => name
        case MulOp(lsub, rsub) => {
          val lstr = ueString(lsub)
          val rstr = ueString(rsub)
          val ls = if (ueAtomicString(lsub)) lstr else s"($lstr)"
          val rs = if (ueAtomicString(rsub)) rstr else s"($rstr)"
          s"$ls * $rs"
        }
        case DivOp(lsub, rsub) => {
          val lstr = ueString(lsub)
          val rstr = ueString(rsub)
          val ls = if (ueAtomicString(lsub)) lstr else s"($lstr)"
          val rs = if (ueAtomicString(rsub)) rstr else s"($rstr)"
          s"$ls / $rs"
        }
        case PowOp(bsub, exp) => {
          val bstr = ueString(bsub)
          val bs = if (ueAtomicString(bsub)) bstr else s"($bstr)"
          s"$bs ^ $exp"
        }
        case PreOp(name, _, sub) => {
          val str = ueString(sub)
          val s = if (ueAtomicString(sub)) str else s"($str)"
          s"${name}-$s"
        }        
        case _ => {
          // This should never execute
          abort(s"Undefined Unit Type: ${typeName(typeU)}")
          ""
        }
      }
    }

    def unitExprString[U: WeakTypeTag]: Tree = {
      val tpeU = weakTypeOf[U]
      val str = ueString(tpeU)
      val sq = q"$str"
      q"""
        _root_.com.manyangled.unit4s.UnitExprString[$tpeU]($sq)
      """
    }
  }
}

package fundamental {
  trait Meter extends FundamentalUnit
  object Meter extends UCompanion[Meter]("meter")

  trait Second extends FundamentalUnit
  object Second extends UCompanion[Second]("second")

  trait Kilogram extends FundamentalUnit
  object Kilogram extends UCompanion[Kilogram]("kilogram")
}

package derived {
  import Integer._
  import fundamental._

  trait Foot extends DerivedUnit[Meter]
  object Foot extends UCompanion[Foot]("foot", 0.3048)

  trait Yard extends DerivedUnit[Meter]
  object Yard extends UCompanion[Yard]("yard", 0.9144)

  trait Minute extends DerivedUnit[Second]
  object Minute extends UCompanion[Minute]("minute", 60.0)

  trait Pound extends DerivedUnit[Kilogram]
  object Pound extends UCompanion[Pound]("pound", 0.453592)

  trait Liter extends DerivedUnit[Meter <^> _3]
  object Liter extends UCompanion[Liter]("liter", 0.001)

  trait EarthGravity extends DerivedUnit[Meter </> (Second <^> _2)]
  object EarthGravity extends UCompanion[EarthGravity]("g", 9.807)
}

package prefix {
  trait Milli extends PrefixUnit
  object Milli extends UCompanion[Milli]("milli", 1e-3)

  trait Kilo extends PrefixUnit
  object Kilo extends UCompanion[Kilo]("kilo", 1e+3)
}

object test {
  import shapeless._
  import prefix._
}
