package com.telenor.possumcore.constants;

/**
 * States describing what the core library is presently doing
 */
public class CoreStatus {
    public static final int Idle = 0; // System is idling
    public static final int Running = 1; // System is gathering
    public static final int Paused = 2; // System is temporary halted
    public static final int Processing = 3; // System is interacting with files
}