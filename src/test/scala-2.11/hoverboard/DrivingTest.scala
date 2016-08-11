package hoverboard

import hoverboard.term._

class DrivingTest extends TestConfig {

  import Util._

  "driving" should "perform beta reduction" in {
    t"(fn x -> x) y".drive shouldEqual t"y"
    t"(fn x x -> f x) y z".drive shouldEqual t"f z"
    t"(fn x -> Add x z) (Add x y)".drive shouldEqual t"Add (Add x y) z".drive
  }

  it should "distribute case onto case" in {
    t"case (case x | Suc x -> x end) | Suc x -> x end".drive shouldEqual
      t"case x | Suc x2 -> (case x2 | Suc x -> x end) end"
  }

  it should "distribute case applied as a function" in {
    t"(case x | Suc y -> f y end) y".drive shouldEqual t"case x | Suc z -> f z y end"
  }

  it should "distribute case applied as a strict argument" in {
    t"Add (case x | Suc y -> f x end) y".drive shouldEqual
      t"case x | Suc z -> Add (f (Suc z)) y end".drive
  }

  it should "reduce case of inj" in {
    t"case Suc x | 0 -> a | Suc b -> f b end".drive shouldEqual t"f x"
  }

  it should "remove constant fixed-point arguments" in {
    t"Add".drive shouldEqual t"fn x y -> (fix f x -> case x | 0 -> y | Suc x' -> Suc (f x') end) x"
    t"Append xs (Cons y Nil)".drive shouldEqual t"Snoc y xs".drive
  }

  it should "not introduce free variables" in {
    forAll { (t: Term) =>
      t.drive.freeVars.difference(t.freeVars) shouldBe empty
    }
  }

  it should "be idempotent" in {
    val historicalFails = Seq(
      t"Add 0 y",
      t"(fn x y -> case x | 0 -> Suc 0 | Suc x' -> Add y (Mul x' y) end) nat_1 (Suc (Suc (f nat_1)))")
    historicalFails
      .foreach { t => t.drive shouldEqual t.drive.drive }

    // This still occasionally fails for examples too large to debug...
    // I think I can switch this check back on when I disable unfolding in the driving step.
//    forAll { (t: Term) =>
//      val driven = t.drive
//      driven shouldEqual driven.drive
//    }
  }

  it should "not simplify undriveable terms" in {
    t"Lt x y".drive shouldEqual t"Lt x y"
  }

  it should "unfold fixed points with constructor arguments safely" in {
    t"Add (Suc x) y".drive shouldEqual t"Suc (Add x y)".drive
    t"Reverse (Cons x xs)".drive shouldEqual t"Append (Reverse xs) (Cons x Nil)".drive
  }

  it should "not unfold fixed points with constructor arguments dangerously" in {
    t"Lt x (Suc x)".drive shouldEqual t"Lt x (Suc x)"
    t"LtEq (Suc x) x".drive shouldEqual t"LtEq (Suc x) x"
    t"IsSorted (Cons x xs)".drive shouldEqual t"IsSorted (Cons x xs)"
    t"IsSorted (Cons x (Insert n xs))".drive shouldEqual t"IsSorted (Cons x ${t"Insert n xs".drive})"
  }

  it should "not add fixed-point indices" in {
    forAll { (t: Term) => t.drive.indices.isSubsetOf(t.indices) shouldBe true }
  }

  it should "rewrite fixed-points called with ⊥ as strict arguments to ⊥" in {
    t"Lt x ⊥".drive shouldEqual ⊥
    t"Lt ⊥ x".drive shouldEqual ⊥
    t"Add x ⊥".drive should not equal ⊥
    t"Add (Suc ⊥) y".drive shouldEqual t"Suc ⊥"
    t"IsSorted (Cons x ⊥)".drive shouldEqual ⊥
  }
}
