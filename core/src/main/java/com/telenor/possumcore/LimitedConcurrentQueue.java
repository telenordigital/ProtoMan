package com.telenor.possumcore;

import java.util.LinkedList;

/**
 * Circular FIFO removing oldest element when new comes in, conserving the most recent elements
 * added
 */
public class LimitedConcurrentQueue<E> extends LinkedList<E> {
    private int limit;

    /**
     * Constructor defining the capacity of the queue
     *
     * @param limit the capacity - must be greater than 0 or it fails
     */
    public LimitedConcurrentQueue(int limit) {
        this.limit = limit;
        if (limit <= 0) throw new IllegalArgumentException("Minimum size is 1");
    }

    @Override
    public boolean add(E o) {
        synchronized (this) {
            super.add(o);
            while (size() > limit) { super.remove(0); }
            return true;
        }
    }
}