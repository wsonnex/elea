package elea.term

import elea.{LispPrintSettings, Name}
import elea.rewrite.Env

import scalaz.Scalaz._
import scalaz.{Name => _, _}

case class App private(fun: Term, args: NonEmptyList[Term]) extends Term with FirstOrder[Term] {
  require(!fun.isInstanceOf[App])

  override def apply(args2: IList[Term]) = App(fun, args :::> args2)

  /**
    * Print the lisp representation of this code
    */
  override def toLisp(settings: LispPrintSettings): String =
    s"(${fun.toLisp(settings)}${args.toList.map(_.toLisp(settings)).mkString(" ", " ", "")})"

  override def reduceHead(env: Env): Term = {
    val reduced = fun.reduceHeadApp(env, args)
    env.matches
      .lookup(reduced)
      // Even though this is a constructor we should re-reduce in case any of its
      // free vars has been matched to something
      .map(_.asTerm.reduce(env))
      .getOrElse(reduced)
  }

  override def reduceHeadApp(env: Env, args2: NonEmptyList[Term]): Term =
    apply(args2.list)

  override def reduceHeadCase(env: Env, enclosingCase: Case): Term =
    fun match {
      case fun: Constructor if !env.alreadySeen(enclosingCase) =>
        fun.reduceCase(args.list, enclosingCase.branches)
          .reduce(env.havingSeen(enclosingCase))
      case _ =>
        super.reduceHeadCase(env, enclosingCase)
    }

  private def flatten: App =
    fun match {
      case fun: App =>
        App(fun.fun, fun.args append args)
      case _ =>
        this
    }

  def mapImmediateSubtermsWithBindings(f: (ISet[Name], Term) => Term): Term =
    App(f(ISet.empty, fun), args.map(t => f(ISet.empty, t)).list)

  def zip(other: Term): Option[IList[(Term, Term)]] =
    other match {
      case other: App if other.args.size == args.size =>
        Some((fun, other.fun) +: args.zip(other.args).list)
      case _ =>
        None
    }

  def arbitraryOrderingNumber: Int = 0

  override def order(other: Term): Ordering =
    other match {
      case other: App =>
        args.size ?|? other.args.size |+|
          fun ?|? other.fun |+|
          args.fzipWith(other.args)(_ ?|? _).concatenate
      case _ =>
        arbitraryOrderingNumber ?|? other.arbitraryOrderingNumber
    }

  override def leftmost: Term = fun.leftmost

  override def deepBranches: IList[Term] =
    args.map(_.deepBranches)
      .sequence[({ type G[X] = IList[X] })#G, Term]
      .map((xs: NonEmptyList[Term]) => App(fun, xs))

  override def unfold: Term =
    fun.unfold.betaReduce(args)

  def isFPPF: Boolean =
    fun match {
      case fun: Fix => fun.isFPPF(args.list)
      case _ => false
    }

  override def unifyLeftUnchecked(to: Term): Option[Substitution] =
    (to, fun) match {
      case (to: App, fun: Var) if to.args.size > args.size =>
        val toArgsRight = to.args.list.takeRight(args.size)
        val funMatch = App(to.fun, to.args.list.dropRight(args.size))
        for {
          argsSub <- Substitution.merge(args.list.fzipWith(toArgsRight)(_ unifyLeft _))
          mergedSub <- Substitution(fun.name -> funMatch) ++ argsSub
        } yield mergedSub
      case _ =>
        super.unifyLeftUnchecked(to)
    }

  override def replace(from: Term, to: Term): Term =
    (this, from) match {
      case AppPrefix(left, from, excessArgs) if (left =@= from) =>
        App(to, excessArgs.map(_.replace(from, to)))
      case _ =>
        super.replace(from, to)
    }
}

object App {
  def apply(fun: Term, args: IList[Term]): Term =
    fun match {
      case fun: App =>
        fun.apply(args)
      case _ =>
        args.toNel
          .map(App(fun, _))
          .getOrElse(fun)
    }
}

/**
  * View any [[Term]] as term application, potentially of zero arguments
  */
object AppView {
  def unapply(term: Term): Option[(Term, IList[Term])] =
    term match {
      case term: App => Some((term.fun, term.args.list))
      case _ => Some((term, IList.empty[Term]))
    }
}

/**
  * Pattern match out terms in fixed-point promoted form
  */
object FPPF {
  def unapply(term: Term): Option[(Fix, IList[Name])] =
    term match {
      case AppView(fun: Fix, args) if fun.isFPPF(args) =>
        Some((fun, args.map(_.asInstanceOf[Var].name)))
      case _ =>
        None
    }
}

object AppPrefix {
  def unapply(terms: (Term, Term)): Option[(App, App, NonEmptyList[Term])] =
    (terms._1, terms._2) match {
      case (longTerm: App, shortTerm: App) if longTerm.args.size > shortTerm.args.size =>
        val longArgs = longTerm.args.list
        val excessArgs = longArgs.takeRight(longArgs.length - shortTerm.args.size).toNel.get
        Some((App(longTerm.fun, longArgs.dropRight(excessArgs.size).toNel.get), shortTerm, excessArgs))
      case _ => None
    }
}
