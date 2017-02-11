package info.kwarc.mmt.mathscheme.rules

import info.kwarc.mmt.api.checking._
import info.kwarc.mmt.api.objects._
import info.kwarc.mmt.api.{DPath, GlobalName, LocalName, utils}
import info.kwarc.mmt.api.symbols.StructuralFeatureRule

object MSTheory {
  val _base = DPath(utils.URI("http", "test.org") / "mathscheme")
  val thpath = _base ? "Meta"
}

class sym(s : String) {
  import MSTheory._
  val path = thpath ? s
  val tm = OMS(path)
  def apply(ls : Term*) = OMA(tm,ls.toList)
  def unapply(t : Term) : Option[List[Term]] = t match {
    case OMS(`path`) => Some(Nil)
    case OMA(`tm`,ls) => Some(ls)
    case _ => None
  }
}

// object Extends extends StructuralFeatureRule("extends")
// object Renaming extends StructuralFeatureRule("RenamingOf")
// object Combine extends StructuralFeatureRule("combine")



object Extends extends {
  val extend = new sym("extends")
} with TheoryExpRule(extend.path) {
  def apply(tm: Term, covered: Boolean)(implicit solver : Solver, stack: Stack, history: History): Boolean = tm match {
    case extend(ls) =>
      ls.forall(p => solver.check(Typing(stack,p,OMS(ModExp.theorytype))))
    case _ => false
  }

  def elaborate(prev : Context, name : Option[LocalName], df : Term)(implicit elab : (Context,Option[LocalName],Term) => Context) : Context = df match {
    case extend(ls) => ls.flatMap(elab(prev,name,_))
    case _ => Nil
  }
}

object Renaming extends {
  val rename = new sym("renaming")
} with TheoryExpRule(rename.path) {
  def apply(tm: Term, covered: Boolean)(implicit solver : Solver, stack: Stack, history: History): Boolean = tm match {
    case rename(List(th1,ComplexTheory(body))) =>
      ???
    case _ => false
  }

  def elaborate(prev : Context, name : Option[LocalName], df : Term)(implicit elab : (Context,Option[LocalName],Term) => Context) : Context = df match {
    case rename(List(th1,ComplexTheory(body))) => ???
    case _ => Nil
  }
}

object Combine extends {
  val combine = new sym("combine")
} with TheoryExpRule(combine.path) {
  def apply(tm: Term, covered: Boolean)(implicit solver : Solver, stack: Stack, history: History): Boolean = tm match {
    case combine(ls) =>
      ls.forall(p => solver.check(Typing(stack,p,OMS(ModExp.theorytype))))
    case _ => false
  }

  def elaborate(prev : Context, name : Option[LocalName], df : Term)(implicit elab : (Context,Option[LocalName],Term) => Context) : Context = df match {
    case combine(ls) => ls.flatMap(elab(prev,name,_))
    case _ => Nil
  }
}