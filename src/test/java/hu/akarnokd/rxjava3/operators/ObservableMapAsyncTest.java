/*
 * Copyright 2016-2019 David Karnok
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package hu.akarnokd.rxjava3.operators;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import hu.akarnokd.rxjava3.test.*;
import io.reactivex.*;
import io.reactivex.disposables.Disposables;
import io.reactivex.functions.*;
import io.reactivex.observers.TestObserver;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class ObservableMapAsyncTest {

    @Test
    public void normal() {
        Observable.range(1, 10)
        .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Integer v)
                    throws Exception {
                return v % 2 == 0 ? Observable.just(v) : Observable.<Integer>empty();
            }
        }))
        .test()
        .assertResult(2, 4, 6, 8, 10);
    }

    @Test
    public void normal2() {
        Observable.range(1, 10)
        .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Integer v)
                    throws Exception {
                return v % 2 == 0 ? Observable.just(v * 2) : Observable.<Integer>empty();
            }
        }))
        .test()
        .assertResult(4, 8, 12, 16, 20);
    }

    @Test
    public void normalMultiInner() {
        Observable.range(1, 10)
        .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Integer v)
                    throws Exception {
                return v % 2 == 0 ? Observable.range(v, 2) : Observable.<Integer>empty();
            }
        }))
        .test()
        .assertResult(2, 4, 6, 8, 10);
    }

    @Test
    public void normalAsync() {
        Observable.range(1, 10)
        .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Integer v)
                    throws Exception {
                Observable<Integer> r = v % 2 == 0 ? Observable.just(v) : Observable.<Integer>empty();
                return r.subscribeOn(Schedulers.computation());
            }
        }))
        .test()
        .awaitDone(5, TimeUnit.SECONDS)
        .assertResult(2, 4, 6, 8, 10);
    }

    @Test
    public void mainError() {
        Observable.<Integer>error(new TestException())
        .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Integer v)
                    throws Exception {
                return v % 2 == 0 ? Observable.just(v) : Observable.<Integer>empty();
            }
        }))
        .test()
        .assertFailure(TestException.class);
    }

    @Test
    public void mainErrorDisposesInner() {
        PublishSubject<Integer> ps1 = PublishSubject.create();
        final PublishSubject<Integer> ps2 = PublishSubject.create();

        TestObserver<Integer> to = ps1
        .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Integer v)
                    throws Exception {
                return ps2;
            }
        }))
        .test();

        assertFalse(ps2.hasObservers());

        ps1.onNext(1);

        assertTrue(ps2.hasObservers());

        ps1.onError(new TestException());

        assertFalse(ps2.hasObservers());

        to.assertFailure(TestException.class);
    }

    @Test
    public void innerError() {
        Observable.range(1, 10)
        .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Integer v)
                    throws Exception {
                return v % 2 == 0
                        ? Observable.<Integer>error(new TestException())
                                : Observable.just(v);
            }
        }))
        .test()
        .assertFailure(TestException.class, 1);
    }

    @Test
    public void innerErrorDisposesInner() {
        PublishSubject<Integer> ps1 = PublishSubject.create();
        final PublishSubject<Integer> ps2 = PublishSubject.create();

        TestObserver<Integer> to = ps1
        .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Integer v)
                    throws Exception {
                return ps2;
            }
        }))
        .test();

        assertFalse(ps2.hasObservers());

        ps1.onNext(1);

        assertTrue(ps2.hasObservers());

        ps2.onError(new TestException());

        assertFalse(ps1.hasObservers());

        to.assertFailure(TestException.class);
    }

    @Test
    public void dispose() {
        PublishSubject<Integer> ps1 = PublishSubject.create();
        final PublishSubject<Integer> ps2 = PublishSubject.create();

        TestObserver<Integer> to = ps1
        .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Integer v)
                    throws Exception {
                return ps2;
            }
        }))
        .test();

        ps1.onNext(1);

        to.dispose();

        assertFalse(ps1.hasObservers());
        assertFalse(ps2.hasObservers());

        to.assertEmpty();
    }

    @Test
    public void isDisposed() {
        PublishSubject<Integer> ps1 = PublishSubject.create();
        final PublishSubject<Integer> ps2 = PublishSubject.create();

        TestHelper.checkDisposed(ps1
                .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
                    @Override
                    public ObservableSource<Integer> apply(Integer v)
                            throws Exception {
                        return ps2;
                    }
                })));
    }

    @Test
    public void doubleOnSubscribe() {
        TestHelper.checkDoubleOnSubscribeObservable(new Function<Observable<Integer>, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Observable<Integer> o)
                    throws Exception {
                return o.compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
                            @Override
                            public ObservableSource<Integer> apply(Integer v)
                                    throws Exception {
                                return Observable.just(v);
                            }
                        }));
            }
        });
    }

    @Test
    public void mapperCrash() {
        Observable.range(1, 10)
        .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Integer v)
                    throws Exception {
                throw new TestException();
            }
        }))
        .test()
        .assertFailure(TestException.class);
    }

    @Test
    public void combinerCrash() {
        Observable.range(1, 10)
        .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
            @Override
            public ObservableSource<Integer> apply(Integer v)
                    throws Exception {
                return Observable.just(1);
            }
        },
        new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer apply(Integer t1, Integer t2) throws Exception {
                throw new TestException();
            }
        }
        ))
        .test()
        .assertFailure(TestException.class);
    }

    @Test
    public void innerIgnoresDispose() {
        List<Throwable> errors = TestHelper.trackPluginErrors();
        try {
            Observable.just(1)
            .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
                @Override
                public ObservableSource<Integer> apply(Integer v)
                        throws Exception {
                    return new Observable<Integer>() {
                        @Override
                        protected void subscribeActual(
                                Observer<? super Integer> observer) {
                            observer.onSubscribe(Disposables.empty());
                            observer.onNext(2);
                            observer.onNext(3);
                            observer.onError(new TestException());
                            observer.onComplete();
                        }
                    };
                }
            }))
            .test()
            .assertResult(2);

            TestHelper.assertUndeliverable(errors, 0, TestException.class);
        } finally {
            RxJavaPlugins.reset();
        }
    }

    @Test
    public void mainErrorsAfterInnerErrors() {
        List<Throwable> errors = TestHelper.trackPluginErrors();
        try {
            new Observable<Integer>() {
                @Override
                protected void subscribeActual(
                        Observer<? super Integer> observer) {
                    observer.onSubscribe(Disposables.empty());
                    observer.onNext(1);
                    observer.onError(new TestException("outer"));
                }
            }
            .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
                @Override
                public ObservableSource<Integer> apply(Integer v)
                        throws Exception {
                    throw new TestException("inner");
                }
            }))
            .test()
            .assertFailure(TestException.class)
            .assertError(TestHelper.assertErrorMessage("inner"));

            TestHelper.assertUndeliverable(errors, 0, TestException.class, "outer");
        } finally {
            RxJavaPlugins.reset();
        }
    }

    @Test
    public void innerErrorsAfterMainErrors() {
        List<Throwable> errors = TestHelper.trackPluginErrors();
        try {
            final AtomicReference<Observer<? super Integer>> refInner = new AtomicReference<Observer<? super Integer>>();

            new Observable<Integer>() {
                @Override
                protected void subscribeActual(
                        Observer<? super Integer> observer) {
                    observer.onSubscribe(Disposables.empty());
                    observer.onNext(1);
                    observer.onError(new TestException("outer"));
                    refInner.get().onError(new TestException("inner"));
                }
            }
            .compose(ObservableTransformers.mapAsync(new Function<Integer, ObservableSource<Integer>>() {
                @Override
                public ObservableSource<Integer> apply(Integer v)
                        throws Exception {
                    return new Observable<Integer>() {
                        @Override
                        protected void subscribeActual(
                                Observer<? super Integer> observer) {
                            observer.onSubscribe(Disposables.empty());
                            refInner.set(observer);
                        }
                    };
                }
            }, 10))
            .test()
            .assertFailure(TestException.class)
            .assertError(TestHelper.assertErrorMessage("outer"));

            TestHelper.assertUndeliverable(errors, 0, TestException.class, "inner");
        } finally {
            RxJavaPlugins.reset();
        }
    }
}