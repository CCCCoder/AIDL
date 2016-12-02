package com.n1njac.test111;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by huanglei on 2016/12/1.
 */

public class BookManagerService extends Service {


    public CopyOnWriteArrayList<Book> mBookList = new CopyOnWriteArrayList<>();

    //用CopyOnWriteArrayList的时候，会发现解注册失败，因为binder会把客户端传过来的对象重新
//    转换生成一个新的对象，所以说注册的listener和解注册的listener不是同一个对象，导致解注册失败
//    因为对象是不能直接跨进程传输的，对象的跨进程传输本质都是反序列化过程。这就是为什么aidl自定义对象
//    都需要实现parcelable。

    //    private CopyOnWriteArrayList<IOnNewBookArrivedListener> mListenerList = new CopyOnWriteArrayList<>();

//    android提供RemoteCallbackList专门用于删除跨进程的listener接口
//    RemoteCallbackList并不是一个list 遍历它的方式需要注意。看onNewBookArrived方法的实现。
    private RemoteCallbackList<IOnNewBookArrivedListener> mListenerList = new RemoteCallbackList<>();

    private AtomicBoolean mIsServiceDestroyed = new AtomicBoolean(false);

    private Binder mBinder = new IBookManager.Stub() {
        @Override
        public List<Book> getBookList() throws RemoteException {
            Log.i("xyz", "service---getBookList" + mBookList.toString());
            Log.d("xyz", "service---getBookList运行在：" + Thread.currentThread());
            return mBookList;
        }

        @Override
        public void addBook(Book book) throws RemoteException {
            Log.i("xyz", "service---addBook");
            mBookList.add(book);
        }

        @Override
        public void registerListener(IOnNewBookArrivedListener listener) throws RemoteException {
//            if (!mListenerList.contains(listener)) {
//                mListenerList.add(listener);
//            } else {
//                Log.d("xyz", "listener already exists");
//            }
//
//            Log.d("xyz", "registerListener's size:" + mListenerList.size());
            mListenerList.register(listener);
            Log.d("xyz","register success");
            Log.d("xyz", "register listener,current size:" + mListenerList.getRegisteredCallbackCount());
        }

        @Override
        public void unregisterListener(IOnNewBookArrivedListener listener) throws RemoteException {
//            if (mListenerList.contains(listener)) {
//                mListenerList.remove(listener);
//                Log.d("xyz", "unregister listener success");
//            } else {
//                Log.d("xyz", "not found ,can not register");
//            }

            mListenerList.unregister(listener);
            Log.d("xyz","unregister success");
            Log.d("xyz", "unregister listener,current size:" + mListenerList.getRegisteredCallbackCount());
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mBookList.add(new Book(1, "android开发艺术探索"));
        mBookList.add(new Book(2, "think in java"));
        //启一个线程每五秒添加一本书
        new Thread(new ServiceWorker()).start();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        mIsServiceDestroyed.set(true);
        super.onDestroy();
    }

    private class ServiceWorker implements Runnable {

        @Override
        public void run() {
            while (!mIsServiceDestroyed.get()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                int bookId = mBookList.size() + 1;
                Book newBook = new Book(bookId, "newBook#" + bookId);
                try {
                    onNewBookArrived(newBook);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //添加了一本新书，就通知所有的注册了listener的用户
    private void onNewBookArrived(Book newBook) throws RemoteException {
        mBookList.add(newBook);
//        for (int i = 0; i < mListenerList.size(); i++) {
//            IOnNewBookArrivedListener listener = mListenerList.get(i);
//            listener.onNewBookArrived(newBook);
//        }
        final int N = mListenerList.beginBroadcast();
        for (int i = 0; i < N; i++) {
            IOnNewBookArrivedListener l = mListenerList.getBroadcastItem(i);
            if (l != null){
                l.onNewBookArrived(newBook);
            }
        }
        mListenerList.finishBroadcast();
    }

}
