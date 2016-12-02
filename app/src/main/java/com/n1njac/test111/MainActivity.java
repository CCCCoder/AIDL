package com.n1njac.test111;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.List;

//扯几句吧。。首先有两个binder线程池，一个客户端的一个服务端的，
//客户端这边这个IOnNewBookArrivedListener，明显的回调机制。服务端实现这个onNewBookArrived，然后客户端
//调用拿到数据，就是这个回调的接口是系统给我们写好的，我们只需要提供一个aidl就好了，并且提供的方法都是运行在binder
//线程池中。

//看到打印的log：onNewBookArrived----运行在：Thread[Binder_2,5,main]
//这是服务器端getBookList方法：service---getBookList运行在：Thread[Binder_1,5,main]
//显然不在一个线程池中，分别是两个binder线程池

//这个程序的大概流程：只讲服务器有新书通知部分：
//首先客户端要通过IBookManager的registerListener注册一个监听器，调用这个方法之后，服务端就会往mListenerList
//中添加一个监听器，然后服务端通过每五秒添加一本新书（模拟有新书来的过程）调用IOnNewBookArrivedListener
//的onNewBookArrived，然后客户端实现这个IOnNewBookArrivedListener重写方法，就接收到了。（回调机制）
//有点不一样的是：因为这是跨进程通信，所以实现这个IOnNewBookArrivedListener用的是他的stub，aidl帮助
//做的工作，本质其实是一样的。
public class MainActivity extends AppCompatActivity {

    public static final int MESSAGE_NEW_BOOK_ARRIVED = 1;

    private IBookManager bookManager;


//    当有新书的时候，服务端会回调客户端IOnNewBookArrivedListener对象中的onNewBookArrived方法，
//    但是这个方法在客户端的binder线程池中执行，所以要切换到主线程中来执行。
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_NEW_BOOK_ARRIVED:
                    Log.d("xyz", "received newBook:" + msg.obj);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };


    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            bookManager = IBookManager.Stub.asInterface(service);


//            下面这里是客户端访问服务器端的方法 ，当客户端访问服务器端的方法的时候，
//            因为服务器端的方法运行在服务器端的binder线程池中
//            而此时客户端被挂起，客户端onServiceConnected这个方法运行在ui线程，如果服务器端的方法
//            比较耗时，那么很可能会造成ANR异常，所以不可以在这里面直接调用服务器端的耗时方法，尽量开启线程
//            来执行。还有就是，由于服务器端的方法本身就是运行在服务器端的binder线程池中，所以服务器可以
//            执行大量耗时操作，这时候切记不要在服务器端开启线程进行异步任务，除非你明确知道自己在干什么。

            try {
                //其实不管是从服务器端拿数据，还是往服务器端添加数据，都相当于回调。
//                定义了IBookManager接口，服务器端实现了这个接口，客户端拿到了IBookManager的引用，这不就是回调吗？
                List<Book> list = bookManager.getBookList();
                Log.i("xyz", "list:" + list.toString());
                Log.i("xyz", list.getClass().getCanonicalName());

                Book newBook = new Book(3, "android讲义");
                bookManager.addBook(newBook);
                List<Book> newList = bookManager.getBookList();
                Log.i("xyz", "list:" + newList.toString());

                bookManager.registerListener(mNewBookArrivedListener);


            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bookManager = null;
            Log.d("xyz","binder died.");
        }
    };


    private IOnNewBookArrivedListener mNewBookArrivedListener = new IOnNewBookArrivedListener.Stub() {
        @Override
        public void onNewBookArrived(Book newBook) throws RemoteException {
            Log.d("xyz","onNewBookArrived----运行在："+Thread.currentThread());
            mHandler.obtainMessage(MESSAGE_NEW_BOOK_ARRIVED, newBook).sendToTarget();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent intent = new Intent(MainActivity.this, BookManagerService.class);
        bindService(intent, conn, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {

        Log.d("xyz","onDestroy");

        if (bookManager != null && bookManager.asBinder().isBinderAlive()) {
            Log.d("xyz", "unregister listener:" + mNewBookArrivedListener);
            try {
                bookManager.unregisterListener(mNewBookArrivedListener);

            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        unbindService(conn);
        super.onDestroy();
    }
}
