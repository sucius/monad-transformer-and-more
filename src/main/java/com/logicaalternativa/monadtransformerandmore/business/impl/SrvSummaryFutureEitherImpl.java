package com.logicaalternativa.monadtransformerandmore.business.impl;

import akka.dispatch.ExecutionContexts;
import com.logicaalternativa.monadtransformerandmore.bean.Sales;
import com.logicaalternativa.monadtransformerandmore.bean.Summary;
import com.logicaalternativa.monadtransformerandmore.business.SrvSummaryFutureEither;
import com.logicaalternativa.monadtransformerandmore.errors.Error;
import com.logicaalternativa.monadtransformerandmore.errors.impl.MyError;
import com.logicaalternativa.monadtransformerandmore.monad.MonadFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceAuthorFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceBookFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceChapterFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceSalesFutEither;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.util.Either;

import java.util.Optional;
import java.util.stream.Collectors;

import static com.logicaalternativa.monadtransformerandmore.monad.MonadFutEitherWrapper.wrap;

public class SrvSummaryFutureEitherImpl implements SrvSummaryFutureEither<Error> {

    private final ServiceBookFutEither<Error> srvBook;
    private final ServiceSalesFutEither<Error> srvSales;
    private final ServiceChapterFutEither<Error> srvChapter;
    private final ServiceAuthorFutEither<Error> srvAuthor;

    private final MonadFutEither<Error> m;


    public SrvSummaryFutureEitherImpl(ServiceBookFutEither<Error> srvBook,
                                      ServiceSalesFutEither<Error> srvSales,
                                      ServiceChapterFutEither<Error> srvChapter,
                                      ServiceAuthorFutEither<Error> srvAuthor,
                                      MonadFutEither<Error> m) {
        super();
        this.srvBook = srvBook;
        this.srvSales = srvSales;
        this.srvChapter = srvChapter;
        this.srvAuthor = srvAuthor;
        this.m = m;
    }


    @Override
    public Future<Either<Error, Summary>> getSummary(Integer idBook) {
        final ExecutionContext ec = ExecutionContexts.global();


        final Future<Either<Error, Optional<Sales>>> sales =
                wrap( srvSales.getSales( idBook ), m )
                .map(  s -> Optional.of( s ) )
                .recover( e -> Optional.empty() )
                .value();

        return wrap(srvBook.getBook(idBook), m)
                .flatMap( book -> {
                            final Future<Either<Error, Summary>> map3 = m.map3(
                                    srvAuthor.getAuthor( book.getIdAuthor() ),
                                    m.sequence (
                                            book.getChapters()
                                                    .stream()
                                                    .map( ch -> srvChapter.getChapter(ch) )
                                                    .collect(Collectors.toList() )
                                    ),
                                    sales,
                                    (author,chapters, salesO) -> new Summary(book, chapters , salesO, author)
                            );
                            return map3;
                        }
                )
                .recoverWith( e -> m.raiseError(  new MyError( "It is impossible to get book summary" ) ))
                .value();

    }

}
