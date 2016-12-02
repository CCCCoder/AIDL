// IBookManager.aidl
package com.n1njac.test111;

import com.n1njac.test111.Book;
import com.n1njac.test111.IOnNewBookArrivedListener;

interface IBookManager {
    List<Book> getBookList();
    void addBook(in Book book);
    void registerListener(IOnNewBookArrivedListener listener);
    void unregisterListener(IOnNewBookArrivedListener listener);
}
