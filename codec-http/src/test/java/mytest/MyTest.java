package mytest;

import io.netty.util.Recycler;
import org.junit.Test;

/**
 * @author bairen
 * @description
 **/
public class MyTest {

    @Test
    public void test() {
        Recycler<HandledObject> recycler = newRecycler();
        recycler.get();
        System.out.println("========================");

    }

    private static Recycler<HandledObject> newRecycler() {
        return new Recycler<HandledObject>() {
            @Override
            protected HandledObject newObject(Handle<HandledObject> handle) {
                return new HandledObject(handle);
            }
        };
    }

    static final class HandledObject {
        Recycler.Handle<HandledObject> handle;

        HandledObject(Recycler.Handle<HandledObject> handle) {
            this.handle = handle;
        }

        void recycle() {
            handle.recycle(this);
        }
    }
}
