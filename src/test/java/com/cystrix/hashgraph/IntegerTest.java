package com.cystrix.hashgraph;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class IntegerTest {
    @Test
    public void test() {
        Integer t = 1;
        count(t);
        assert t.intValue() == 10;
    }

    @Test
    public void test2(){
        AtomicInteger t = new AtomicInteger(1);
        count(t);
        assert  t.get() == 2;
    }

    public void count(Integer n) {
        n = 10;
    }

    public void count(AtomicInteger n) {
        n.getAndIncrement();
    }
}
