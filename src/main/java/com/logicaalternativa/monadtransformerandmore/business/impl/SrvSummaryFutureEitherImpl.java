package com.logicaalternativa.monadtransformerandmore.business.impl;

import com.logicaalternativa.monadtransformerandmore.bean.*;
import com.logicaalternativa.monadtransformerandmore.errors.impl.MyError;
import scala.Tuple2;
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

import static com.logicaalternativa.monadtransformerandmore.util.Java8.recoverF;

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

        Optional<Sales> sales = Optional.empty();
        if (salesEither.isRight())
            sales = Optional.of(salesEither.right().get());

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

        Summary summary = new Summary(b, chapters, sales, a);
        return new Right(summary);
    }


    @Override
    public Future<Either<Error, Summary>> getSummary(Integer idBook) {
        final ExecutionContext ec = ExecutionContexts.global();

        final Future<Either<Error, Book>> book = srvBook.getBook(idBook).recover(
                recoverF(e -> new Left<>(new MyError(e.getMessage())))
                , ec);
        final Future<Either<Error, Sales>> sales = srvSales.getSales(idBook);
        final Future<Either<Error, Author>> author = book.flatMap(this::raiseBook, ec);
        final Future<Either<Error, List<Chapter>>> listChapters = book.flatMap((Either<Error, Book> b) -> raiseChapters(ec, b) ,ec);

        Future<Tuple2<Either<Error, Book>, Either<Error, Sales>>> zipBookAndSales = book.zip(sales);
        Future<Tuple2<Either<Error, List<Chapter>>, Either<Error, Author>>> zipAuthorAndChapters = listChapters.zip(author);

        Future<Tuple2<Tuple2<Either<Error, Book>,
                Either<Error, Sales>>,
                Tuple2<Either<Error, List<Chapter>>, Either<Error, Author>>>> zipTotal = zipBookAndSales.zip(zipAuthorAndChapters);

        return zipTotal.map(SrvSummaryFutureEitherImpl::applyFunction, ec);

    }

    private Future<Either<Error, List<Chapter>>> raiseChapters(ExecutionContext ec, Either<Error, Book> b) {
        if (b.isRight()) {
            List<Future<Either<Error, Chapter>>> collect = b.right().get()
                    .getChapters()
                    .stream()
                    .map(l -> srvChapter.getChapter(l))
                    .collect(Collectors.toList());
            Future<Iterable<Either<Error, Chapter>>> sequence = Futures.sequence(collect, ec);
            return sequence.map(p -> {
                final Map<Boolean, List<Either<Error, Chapter>>> groupBy =
                        StreamSupport.stream(p.spliterator(), true)
                                .collect(Collectors.groupingBy(Either::isRight));
                if (groupBy.get(false) != null && !groupBy.get(false).isEmpty()) {
                    return new Left<>(groupBy.get(false).get(0).left().get());
                }
                final List<Chapter> chapterList = groupBy.get(true)
                        .stream()
                        .map(s -> s.right().get())
                        .collect(Collectors.toList());

                return new Right<>(chapterList);
            }, ec);
        }
        return Futures.successful(new Left(b.left().get()));
    }

    private Future<Either<Error, Author>> raiseBook(Either<Error, Book> b) {
        if (b.isLeft()) {
            return Futures.successful(new Left(b.left()));
        }
        return srvAuthor.getAuthor(b.right().get().getIdAuthor());
    }
}
