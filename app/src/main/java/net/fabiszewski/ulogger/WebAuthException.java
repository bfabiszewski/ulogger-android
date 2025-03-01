/*
 * Copyright (c) 2017 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * This file is part of μlogger-android.
 * Licensed under GPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */

package net.fabiszewski.ulogger;

/**
 * Web authentication exception
 *
 */

public class WebAuthException extends Exception {

    public WebAuthException(String message) {
        super(message);
    }
}
