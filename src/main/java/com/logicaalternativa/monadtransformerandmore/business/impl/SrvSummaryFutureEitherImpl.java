package com.logicaalternativa.monadtransformerandmore.business.impl;

import com.logicaalternativa.monadtransformerandmore.bean.*;
import com.logicaalternativa.monadtransformerandmore.errors.impl.MyError;
import scala.Tuple2;
import scala.collection.immutable.Stream;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.util.Either;
import akka.dispatch.ExecutionContexts;
import akka.dispatch.Futures;

import com.logicaalternativa.monadtransformerandmore.business.SrvSummaryFutureEither;
import com.logicaalternativa.monadtransformerandmore.errors.Error;
import com.logicaalternativa.monadtransformerandmore.monad.MonadFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceAuthorFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceBookFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceChapterFutEither;
import com.logicaalternativa.monadtransformerandmore.service.future.ServiceSalesFutEither;
import scala.util.Left;
import scala.util.Right;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static akka.dispatch.Futures.successful;
import static java.util.Collections.singleton;

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

    private static Either<Error, Summary> applyFunction(Tuple2<Tuple2<Either<Error, Book>,
            Either<Error, Sales>>,
            Tuple2<Either<Error, List<Chapter>>, Either<Error, Author>>> t22) {
        Either<Error, Book> bookEither = t22._1._1;
        Either<Error, Sales> salesEither = t22._1._2;
        Either<Error, Author> authorEither = t22._2._2;
        Either<Error, List<Chapter>> errorListEither = t22._2._1;

        Book b;
        if (bookEither.isRight())
            b = bookEither.right().get();
        else
            return new Left(new MyError("It is impossible to get book summary"));

        Sales s;
        if (salesEither.isRight())
            s = salesEither.right().get();
        else
            return new Left(new MyError("It is impossible to get book summary"));

        Author a;
        if (authorEither.isRight())
            a = authorEither.right().get();
        else
            return new Left(new MyError("It is impossible to get book summary"));


        List<Chapter> chapters;

        if (errorListEither.isRight())
            chapters = errorListEither.right().get();
        else
            return new Left(new MyError("It is impossible to get book summary"));

        Summary summary = new Summary(b, chapters, Optional.of(s), a);
        return new Right(summary);
    }


    @Override
    public Future<Either<Error, Summary>> getSummary(Integer idBook) {
        final ExecutionContext ec = ExecutionContexts.global();

        final Future<Either<Error, Book>> book = srvBook.getBook(idBook);
        final Future<Either<Error, Sales>> sales = srvSales.getSales(idBook);
        final Future<Either<Error, Author>> author = book.flatMap(this::raiseBook, ec);
        final Future<Either<Error, List<Chapter>>> listChapters = book.map((Either<Error, Book> b) -> {
                    if (b.isRight()) {
                        List<Future<Either<Error, Chapter>>> collect = b.right().get()
                                .getChapters()
                                .stream()
                                .map(l -> srvChapter.getChapter(l))
                                .collect(Collectors.toList());
                        Future<Iterable<Either<Error, Chapter>>> sequence = Futures.sequence(collect, ec);
                        Future<List<Chapter>> map = sequence.map(p -> StreamSupport.stream(p.spliterator(), true).map(c -> c.right().get()).collect(Collectors.toList()), ec);
                        return new Right(map.map(t -> StreamSupport.stream(t.spliterator(), true).collect(Collectors.toList()), ec));
                    }
                    return new Left(b.left());
                }
                , ec);

        Future<Tuple2<Either<Error, Book>, Either<Error, Sales>>> zipBookAndSales = book.zip(sales);
        Future<Tuple2<Either<Error, List<Chapter>>, Either<Error, Author>>> zipAuthorAndChapters = listChapters.zip(author);

        Future<Tuple2<Tuple2<Either<Error, Book>,
                Either<Error, Sales>>,
                Tuple2<Either<Error, List<Chapter>>, Either<Error, Author>>>> zipTotal = zipBookAndSales.zip(zipAuthorAndChapters);

        return zipTotal.map(SrvSummaryFutureEitherImpl::applyFunction, ec);

    }

    private Future<Either<Error, Author>> raiseBook(Either<Error, Book> b) {
        if (b.isLeft()) {
            return Futures.successful(new Left(b.left()));
        }
        return srvAuthor.getAuthor(b.right().get().getIdAuthor());
    }
}
