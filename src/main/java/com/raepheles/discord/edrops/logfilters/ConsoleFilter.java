package com.raepheles.discord.edrops.logfilters;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Created by Rae on 5/4/2018.
 * Filter rules:
 * Accept if log level is INFO, WARN or ERROR
 * Deny everything else
 *
 * Used for console logs.
 */
public class ConsoleFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {

        if(event.getLevel().isGreaterOrEqual(Level.INFO))
            return FilterReply.ACCEPT;
        else
            return FilterReply.DENY;

    }
}
