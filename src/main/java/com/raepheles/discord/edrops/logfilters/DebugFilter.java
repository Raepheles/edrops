package com.raepheles.discord.edrops.logfilters;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Created by Rae on 5/4/2018.
 * Filter rules:
 * Deny if log level is TRACE
 * Deny if log marker is MESSAGES
 * Deny if log marker is EVENTS and starts with "user"
 *     - More specifically logs that are marked as EVENTS and starts with "user" are user join/leave guild logs.
 * Deny if log marker is PRESENCES
 * Accept everything else
 *
 * Used for debug logs.
 */
public class DebugFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if(event.getLevel().isGreaterOrEqual(Level.DEBUG)) {
            return FilterReply.ACCEPT;
        } else {
            return FilterReply.DENY;
        }

    }
}
