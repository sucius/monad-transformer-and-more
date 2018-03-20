package com.logicaalternativa.monadtransformerandmore
package monad
package container

import com.logicaalternativa.monadtransformerandmore.container._
import errors._
import errors.impl._

import MonadContainerErrorS.ContainerError

object MonadContainerErrorS {

  type ContainerError[T] = Container[Error, T]

  def apply() = new MonadContainerErrorS

}

class MonadContainerErrorS extends Monad[Error, ContainerError] {

  def pure[T](value: T): ContainerError[T] = Container.value(value)

  def flatMap[A, T](from: ContainerError[A], f: (A) => ContainerError[T]): ContainerError[T] = {
    if (from.isOk) {
      fromException(f(from.getValue))
    } else {
      raiseError(from.getError)
    }
  }

  def raiseError[T](error: Error): ContainerError[T] = Container.error(error)

  def recoverWith[T](from: ContainerError[T], f: (Error) => ContainerError[T]): ContainerError[T] = {
    if (from.isOk) {
      from
    } else {
      fromException(f(from.getError))
    }
  }

  //Lazy evaluation of con
  private def fromException[T](con: => ContainerError[T]): ContainerError[T] = {
    try {
      con
    } catch {
      case t: Throwable => raiseError(new MyError(t.getMessage))
    }
  }

}
