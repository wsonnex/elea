

import elea.term._

import scalaz.{Name => _, _}
import Scalaz._

package object elea {
  val optIntOrder = implicitly[Order[Option[Int]]]
  val stringOrder = implicitly[Order[String]]

  implicit def orderName = new Order[Name] {
    override def order(x: Name, y: Name): Ordering =
      stringOrder(x.name, y.name) |+| optIntOrder(x.freshener, y.freshener)
  }

  implicit class WrappedString(string: String) {
    def indent: String = string.replace("\n", "\n  ")
    def indentNewBlock: String = s"\n$string".indent
  }

  implicit class WrappedStringContext(context: StringContext)(implicit program: Program) {
    def term(args: Any*): Term = {
      // Any interpolated terms are replaced by variables for the parsing step, and then substituted
      // for the provided term after parsing.
      var termArgSubst = Substitution.empty
      val argStrings: Seq[String] = args
        .map {
          case arg: Name => Var(arg)
          case arg => arg
        }
        .map {
          case arg: Term =>
            val termVar = Name("__INTERPOLATED" + termArgSubst.size)
            termArgSubst = termArgSubst +! (termVar -> arg)
            termVar.toString
          case arg =>
            arg.toString
        }
      val termDef = context.standardInterpolator(x => x, argStrings)
      OldParser.parseTerm(termDef) :/ termArgSubst
    }

    def lterm(args: Any*): Term = {
      // Any interpolated terms are replaced by variables for the parsing step, and then substituted
      // for the provided term after parsing.
      var termArgSubst = Substitution.empty
      val argStrings: Seq[String] = args
        .map {
          case arg: Name => Var(arg)
          case arg => arg
        }
        .map {
          case arg: Term =>
            val termVar = Name("__INTERPOLATED" + termArgSubst.size)
            termArgSubst = termArgSubst +! (termVar -> arg)
            termVar.toString
          case arg =>
            arg.toString
        }
      val termDef = context.standardInterpolator(x => x, argStrings)
      Parser.parseTerm(termDef) :/ termArgSubst
    }
  }

  implicit class WrappedIList[A](list: IList[A]) {
    def removeAt(n: Int): Option[IList[A]] = {
      val (left, right) = list.splitAt(n)
      right.tailOption.map(left ++ _)
    }

    def setAt(n: Int, elem: A): IList[A] = {
      val (left, right) = list.splitAt(n)
      left ++ (elem :: right.tailOption.getOrElse(INil[A]()))
    }

    def unzip3[B, C, D](implicit ev: A <:< (B, C, D)): (IList[B], IList[C], IList[D]) =
      list match {
        case INil() =>
          (INil(), INil(), INil())
        case ICons(head, tail) =>
          val (tail1, tail2, tail3) = tail.unzip3
          (ICons(ev(head)._1, tail1), ICons(ev(head)._2, tail2), ICons(ev(head)._3, tail3))
      }

    def embedsInto(other: IList[A]): Boolean =
      (list, other) match {
        case (INil(), _) => true
        case (_, INil()) => false
        case (ICons(x, xs), ICons(y, ys)) if x == y =>
          xs embedsInto ys
        case (xs, ICons(_, ys)) =>
          xs embedsInto ys
      }
  }

  def first[A, B, C](p: (A, B))(f: A => C): (C, B) = (f(p._1), p._2)

  implicit class WrappedAny[A](a: A) {
    def tap(fun: A => Unit): A = {
      fun(a); a
    }
  }

  implicit def stringToName(name: String): Name = Name(name)
}
