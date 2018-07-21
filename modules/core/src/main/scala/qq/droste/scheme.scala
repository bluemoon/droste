package qq.droste

import cats.Functor
import cats.Monad
import cats.Traverse

import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.traverse._

import syntax.all._
import implicits.composedFunctor._

/**
  * @groupname refolds Refolds
  * @groupname folds   Folds
  * @groupname unfolds Unfolds
  * @groupname exotic  Exotic
  */
object scheme {

  // note:
  // All core morphisms are defined in an algebra centric manner.
  // All parameters are algebras and a function-- the morphism-- is
  // returned.

  /** Build a hylomorphism by recursively unfolding with `coalgebra` and
    * refolding with `algebra`.
    *
    * <pre>
    *                  hylo
    *          A ---------------> B
    *          |                  ^
    *  co-     |                  |
    * algebra  |                  | algebra
    *          |                  |
    *          v                  |
    *         F[A] ------------> F[B]
    *                map hylo
    * </pre>
    *
    * @group refolds
    *
    * @usecase def hylo[F[_], A, B](algebra: Algebra[F, B], coalgebra: Coalgebra[F, A]): A => B
    *   @inheritdoc
    */
  def hylo[F[_]: Functor, A, B](
    algebra  : F[B] => B,
    coalgebra: A    => F[A]
  ): A => B =
    new (A => B) {
      def apply(a: A): B = algebra(coalgebra(a).map(this))
    }

  /** Convenience to build a hylomorphism for the composed functor `F[G[_]]`.
    *
    * This is strictly for convenience and just delegates
    * to `hylo` with the types set properly.
    *
    * @group refolds
    *
    * @usecase def hyloC[F[_], G[_], A, B](algebra: Algebra[(F ∘ G)#λ, B], coalgebra: Coalgebra[(F ∘ G)#λ, A]): A => B
    *   @inheritdoc
    */
  @inline def hyloC[F[_]: Functor, G[_]: Functor, A, B](
    algebra  : F[G[B]] => B,
    coalgebra: A       => F[G[A]]
  ): A => B = hylo[(F ∘ G)#λ, A, B](algebra, coalgebra)

  /** Build a monadic hylomorphism
    *
    * <pre>
    *                 hyloM
    *          A ---------------> M[B]
    *          |                  ^
    *  co-     |                  |
    * algebraM |                  | flatMap f
    *          |                  |
    *          v                  |
    *       M[F[A]] ---------> M[F[M[B]]]
    *               map hyloM
    *
    * with f:
    *
    * F[M[B]] -----> M[F[B]] ----------> M[B]
    *       sequence          flatMap
    *                         algebraM
    * </pre>
    *
    * @group refolds
    *
    * @usecase def hyloM[M[_], F[_], A, B](algebra: AlgebraM[M, F, B], coalgebra: CoalgebraM[M, F, A]): A => M[B]
    *   @inheritdoc
    */
  def hyloM[M[_]: Monad, F[_]: Traverse, A, B](
    algebra  : AlgebraM[M, F, B],
    coalgebra: CoalgebraM[M, F, A]
  ): A => M[B] =
    hyloC[M, F, A, M[B]](
      _.flatMap(_.sequence.flatMap(algebra)),
      coalgebra)

  def ana[F[_]: Functor, A, R](
    coalgebra: Coalgebra[F, A]
  )(implicit embed: Embed[F, R]): A => R =
    hylo(
      embed.algebra.run,
      coalgebra.run)

  def cata[F[_]: Functor, R, B](
    algebra: Algebra[F, B]
  )(implicit project: Project[F, R]): R => B =
    hylo(
      algebra.run,
      project.coalgebra.run)

  def anaM[M[_]: Monad, F[_]: Traverse, A, R](
    coalgebraM: CoalgebraM[M, F, A]
  )(implicit embed: Embed[F, R]): A => M[R] =
    hyloM(
      embed.algebra.lift[M],
      coalgebraM)

  def cataM[M[_]: Monad, F[_]: Traverse, R, B](
    algebraM: AlgebraM[M, F, B]
  )(implicit project: Project[F, R]): R => M[B] =
    hyloM(
      algebraM,
      project.coalgebra.lift[M])

  def ghylo[SA, SB, F[_]: Functor, A, B](
    algebra  : GAlgebra  [SB, F, B],
    coalgebra: GCoalgebra[SA, F, A])(
    gather   : Gather    [SB, F, B],
    scatter  : Scatter   [SA, F, A]
  ): A => B =
    a => algebra(coalgebra(a).map(
      hylo[F, SA, SB](
        fb => gather(algebra(fb), fb),
        sa => scatter(sa).fold(coalgebra.run, identity))))

  def ghylo[SA, SB, F[_]: Functor, A, B](
    gathered : GAlgebra.Gathered   [SB, F, B],
    scattered: GCoalgebra.Scattered[SA, F, A]
  ): A => B =
    ghylo(
      gathered.algebra, scattered.coalgebra)(
      gathered.gather, scattered.scatter)


  def gcata[S, F[_]: Functor, R, B](
    galgebra: GAlgebra[S, F, B])(
    gather  : Gather  [S, F, B]
  )(implicit project: Project[F, R]): R => B =
    r => galgebra(project.coalgebra(r).map(
      hylo[F, R, S](
        fb => gather(galgebra(fb), fb),
        project.coalgebra.run)))

  def gcata[S, F[_]: Functor, R, B](
    gathered: GAlgebra.Gathered[S, F, B]
  )(implicit project: Project[F, R]): R => B =
    gcata(gathered.algebra)(gathered.gather)


  def gana[S, F[_]: Functor, A, R](
    coalgebra: GCoalgebra[S, F, A])(
    scatter  : Scatter   [S, F, A]
  )(implicit embed: Embed[F, R]): A => R =
    a => embed.algebra(coalgebra(a).map(
      hylo[F, S, R](
        embed.algebra.run,
        s => scatter(s).fold(coalgebra.run, identity))))

  def gana[S, F[_]: Functor, A, R](
    scattered: GCoalgebra.Scattered[S, F, A]
  )(implicit embed: Embed[F, R]): A => R =
    gana(scattered.coalgebra)(scattered.scatter)

  /** A petting zoo for wild and exotic animals we keep separate from
    * the regulars in [[scheme]]. For their safety and yours.
    *
    * @group exotic
    *
    * @groupname refolds Rambunctious Refolds
    * @groupname folds   Fantastic Folds
    * @groupname unfolds Unusual Unfolds
    */
  object zoo extends Zoo

  /** Convenience to specify the base constructor "shape" (such as `Fix`
    * or `Cofree[?[_], Int]`) for recursion.
    *
    * This helps to guide Scala's type inference so all of the type
    * parameters for the various recursion scheme methods don't have
    * to be provided.
    *
    * @usecase def apply[Shape]: SchemePartialBasis[Shape, Shape]
    *   @inheritdoc
    */
  def apply[PatR[_[_]]](implicit ev: Basis.Solve[PatR]): SchemePartialBasis[PatR, ev.PatF] = new SchemePartialBasis[PatR, ev.PatF]

  final class SchemePartialBasis[PatR[_[_]], PatF[_[_], _]] private[droste]() {

    type EmbedP[F[_]]    = Embed[PatF[F, ?], PatR[F]]
    type ProjectP[F[_]]  = Project[PatF[F, ?], PatR[F]]
    type FunctorP[F[_]]  = Functor[PatF[F, ?]]
    type TraverseP[F[_]] = Traverse[PatF[F, ?]]

    def ana[F[_], A](
      coalgebra: Coalgebra[PatF[F, ?], A]
    )(implicit embed: EmbedP[F], ev: FunctorP[F]): A => PatR[F] =
      scheme.ana[PatF[F, ?], A, PatR[F]](coalgebra)

    def anaM[M[_]: Monad, F[_], A](
      coalgebraM: CoalgebraM[M, PatF[F, ?], A]
    )(implicit embed: EmbedP[F], ev: TraverseP[F]): A => M[PatR[F]] =
      scheme.anaM[M, PatF[F, ?], A, PatR[F]](coalgebraM)

    def cata[F[_], B](
      algebra: Algebra[PatF[F, ?], B]
    )(implicit project: ProjectP[F], ev: FunctorP[F]): PatR[F] => B =
      scheme.cata[PatF[F, ?], PatR[F], B](algebra)

  }

}
