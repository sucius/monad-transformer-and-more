package com.logicaalternativa.monadtransformerandmore
package business

import monad._
import bean._
import service._

import monad.syntax.Implicits._

import java.util.Optional

trait SrvSummaryF[E, P[_]] {

  implicit val E: Monad[E, P]

  import E._

  val srvBook: ServiceBookF[E, P]
  val srvSales: ServiceSalesF[E, P]
  val srvChapter: ServiceChapterF[E, P]
  val srvAuthor: ServiceAuthorF[E, P]

  def getSummary(idBook: Int): P[Summary] = {

    val bookP = srvBook getBook idBook
    val salesP = srvSales.getSales(idBook)
      .map(Optional.of(_))
      .recover(_ => Optional.empty[Sales])


    for {
      book <- bookP
      sales <- salesP
      author <- srvAuthor.getAuthor(book.getIdAuthor)
    } yield (new Summary(book, null, sales, author))

  }

  protected[SrvSummaryF] def getGenericError(s: String): E

}
