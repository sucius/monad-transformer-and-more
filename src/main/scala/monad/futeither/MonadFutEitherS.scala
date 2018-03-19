package com.logicaalternativa.monadtransformerandmore
package monad
package futeither

import errors._
import errors.impl._

import scala.concurrent._
import scala.util._
import scala.concurrent.ExecutionContext
import MonadFutEitherS.FutEitherError

import scala.util.matching.Regex.Match

object MonadFutEitherS {

  // import scala.concurrent.ExecutionContext.Implicits.global

  type FutEitherError[T] = Future[Either[Error, T]]

  def apply(implicit ec: ExecutionContext) = new MonadFutEitherS

}

class MonadFutEitherS(implicit ec: ExecutionContext) extends Monad[Error, FutEitherError] {

  def pure[T](value: T): FutEitherError[T] = Future {
    Right(value)
  }

  def flatMap[A, T](from: FutEitherError[A], f: (A) => FutEitherError[T]): FutEitherError[T] = {
    from.flatMap {
      case Right(a) => f(a)
      case Left(e) => raiseError(e)
    }.recoverWith(recoverFunction)
  }

  def raiseError[T](error: Error): FutEitherError[T] = Future {
    Left(error)
  }

  def recoverWith[T](from: FutEitherError[T], f: (Error) => FutEitherError[T]): FutEitherError[T] = {
    from.flatMap {
      case Right(a) => pure(a)
      case Left(e) => f(e)
    }.recoverWith(recoverFunction)
  }

  private def recoverFunction[T]: PartialFunction[Throwable, FutEitherError[T]] = {
    case t: Throwable => raiseError(new MyError(t.getMessage))
  }


}
